package com.droidlens.instrumentation

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.droidlens.model.FrameMetrics
import com.droidlens.model.JankEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instruments frame timings using [Choreographer.FrameCallback].
 * Dispatches analysis work to [Dispatchers.Default] to avoid main thread jank.
 */
class FrameInstrumentation(
    private val frameJankThresholdMs: Long = 16L
) : Choreographer.FrameCallback {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    
    private var lastFrameTimeNs: Long = 0
    private var isStarted = false

    private val frameBuffer = CircularFrameBuffer(300)
    private var totalJankFrames = 0
    private var consecutiveJankCount = 0

    private val _metrics = MutableStateFlow(FrameMetrics.empty())
    val metrics: StateFlow<FrameMetrics> = _metrics.asStateFlow()

    private val _jankEvents = MutableSharedFlow<JankEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val jankEvents: SharedFlow<JankEvent> = _jankEvents.asSharedFlow()

    private val rawDurations = MutableSharedFlow<Long>(
        extraBufferCapacity = 300,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            rawDurations.collect { durationNs ->
                processFrame(durationNs)
            }
        }
    }

    /**
     * Starts the frame instrumentation.
     */
    fun start() {
        if (isStarted) return
        isStarted = true
        lastFrameTimeNs = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    /**
     * Stops the frame instrumentation.
     */
    fun stop() {
        isStarted = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isStarted) return

        val startTime = System.nanoTime()

        if (lastFrameTimeNs > 0) {
            val durationNs = frameTimeNanos - lastFrameTimeNs
            // Immediately dispatch to background thread
            rawDurations.tryEmit(durationNs)
        }
        lastFrameTimeNs = frameTimeNanos

        // Schedule next frame
        Choreographer.getInstance().postFrameCallback(this)

        val overheadNs = System.nanoTime() - startTime
        if (overheadNs > 50_000) { // 0.05ms
            Log.w("DroidLens-Frame", "Instrumentation overhead exceeded 0.05ms: ${overheadNs / 1_000_000.0}ms")
        }
    }

    private fun processFrame(durationNs: Long) {
        frameBuffer.write(durationNs)
        
        val durationMs = durationNs / 1_000_000.0f
        val isJanking = durationMs > frameJankThresholdMs

        if (isJanking) {
            totalJankFrames++
            consecutiveJankCount++
            _jankEvents.tryEmit(
                JankEvent(
                    frameTimeMs = durationMs,
                    timestampMs = SystemClock.elapsedRealtime(),
                    consecutiveJankCount = consecutiveJankCount
                )
            )
        } else {
            consecutiveJankCount = 0
        }

        updateMetrics(isJanking)
    }

    private fun updateMetrics(isJankingNow: Boolean) {
        val allFrames = frameBuffer.readAll()
        if (allFrames.isEmpty()) return

        // Use last 60 frames for FPS and Avg (approx 1-2 seconds)
        val last60Count = minOf(allFrames.size, 60)
        var sum60 = 0L
        for (i in 0 until last60Count) {
            sum60 += allFrames[allFrames.size - 1 - i]
        }
        
        val avg60Ns = sum60 / last60Count.toFloat()
        val avg60Ms = avg60Ns / 1_000_000.0f
        val currentFps = if (avg60Ms > 0) 1000f / avg60Ms else 0f

        _metrics.value = FrameMetrics(
            currentFps = currentFps,
            avgFrameTimeMs = avg60Ms,
            p95FrameTimeMs = frameBuffer.percentile(0.95f),
            jankFrameCount = totalJankFrames,
            isJanking = isJankingNow
        )
    }

    /**
     * Lock-free circular buffer for storing frame durations.
     */
    inner class CircularFrameBuffer(private val size: Int) {
        private val buffer = LongArray(size)
        private val writeIndex = AtomicInteger(0)
        private var isFull = false

        /**
         * Writes a frame duration in nanoseconds.
         */
        fun write(frameTimeNs: Long) {
            val index = writeIndex.getAndIncrement() % size
            buffer[index] = frameTimeNs
            if (index == size - 1) isFull = true
        }

        /**
         * Reads all stored durations.
         */
        fun readAll(): LongArray {
            val currentWrite = writeIndex.get()
            val count = if (isFull) size else (currentWrite % size)
            val result = LongArray(count)
            
            if (!isFull) {
                System.arraycopy(buffer, 0, result, 0, count)
            } else {
                val start = currentWrite % size
                System.arraycopy(buffer, start, result, 0, size - start)
                System.arraycopy(buffer, 0, result, size - start, start)
            }
            return result
        }

        /**
         * Calculates the p-th percentile value in milliseconds.
         */
        fun percentile(p: Float): Float {
            val data = readAll()
            if (data.isEmpty()) return 0f
            data.sort()
            val index = (p * (data.size - 1)).toInt()
            return data[index] / 1_000_000.0f
        }

        /**
         * Calculates the average frame time in milliseconds.
         */
        fun average(): Float {
            val data = readAll()
            if (data.isEmpty()) return 0f
            return (data.sum() / data.size.toFloat()) / 1_000_000.0f
        }

        /**
         * Clears the buffer.
         */
        fun clear() {
            writeIndex.set(0)
            isFull = false
            buffer.fill(0L)
        }
    }
}
