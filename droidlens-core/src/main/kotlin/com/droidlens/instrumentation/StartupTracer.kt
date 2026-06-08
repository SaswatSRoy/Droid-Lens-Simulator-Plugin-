package com.droidlens.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Window
import com.droidlens.model.StartupMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Measures app startup in three phases using [android.os.Trace] sections:
 *
 * 1. **Application.onCreate()** — from [markOnCreateStart] to [markOnCreateEnd]
 * 2. **Time to First Frame** — from onCreate end to first [Choreographer] callback
 * 3. **Time to Interactive** — from first frame to first user [MotionEvent]
 *
 * Each phase is also instrumented with [android.os.Trace.beginSection]/[android.os.Trace.endSection]
 * so they appear in Android Studio's system trace profiler.
 *
 * Usage:
 * ```
 * // In DroidLensInitializer (earliest possible point):
 * StartupTracer.markProcessStart()
 *
 * // In Application.onCreate():
 * StartupTracer.markOnCreateStart()
 * // ... app init code ...
 * StartupTracer.markOnCreateEnd(application)
 * ```
 */
object StartupTracer {

    private const val TAG = "DroidLens-Startup"

    // Timestamps in milliseconds (SystemClock.elapsedRealtime)
    private var processStartMs: Long = 0L
    private var onCreateStartMs: Long = 0L
    private var onCreateEndMs: Long = 0L
    private var firstFrameMs: Long = 0L
    private var firstInteractiveMs: Long = 0L

    private var isFirstFrameCaptured = false
    private var isInteractiveCaptured = false

    private val _metrics = MutableStateFlow<StartupMetrics?>(null)

    /**
     * Real-time startup metrics. Emits null until at least Application.onCreate() is measured.
     * Updates as each phase is captured.
     */
    val metrics: StateFlow<StartupMetrics?> = _metrics.asStateFlow()

    /**
     * Records the earliest possible timestamp (called from [DroidLensInitializer]).
     * This is as close to process creation as we can get via Jetpack Startup.
     */
    fun markProcessStart() {
        if (processStartMs == 0L) {
            processStartMs = SystemClock.elapsedRealtime()
            android.os.Trace.beginSection("DroidLens:TotalStartup")
            Log.d(TAG, "Process start marked at: ${processStartMs}ms")
        }
    }

    /**
     * Marks the beginning of Application.onCreate().
     * Call this as the first line in your Application.onCreate().
     */
    fun markOnCreateStart() {
        onCreateStartMs = SystemClock.elapsedRealtime()
        android.os.Trace.beginSection("DroidLens:Application.onCreate")
        Log.d(TAG, "onCreate start at: ${onCreateStartMs}ms")
    }

    /**
     * Marks the end of Application.onCreate() and starts listening for the first frame.
     * Call this as the last line in your Application.onCreate().
     *
     * @param application The Application instance, used to register lifecycle callbacks
     *                    for intercepting the first MotionEvent.
     */
    fun markOnCreateEnd(application: Application) {
        onCreateEndMs = SystemClock.elapsedRealtime()
        android.os.Trace.endSection() // End DroidLens:Application.onCreate

        val onCreateDuration = onCreateEndMs - onCreateStartMs
        Log.d(TAG, "onCreate end at: ${onCreateEndMs}ms (duration: ${onCreateDuration}ms)")

        // Start listening for first Choreographer frame
        android.os.Trace.beginSection("DroidLens:TimeToFirstFrame")
        listenForFirstFrame()

        // Register activity lifecycle callbacks to intercept first MotionEvent
        application.registerActivityLifecycleCallbacks(FirstInputCallback())

        // Emit partial metrics
        emitMetrics()
    }

    /**
     * Listens for the first Choreographer frame callback after Application.onCreate().
     */
    private fun listenForFirstFrame() {
        Choreographer.getInstance().postFrameCallback { _ ->
            if (!isFirstFrameCaptured) {
                firstFrameMs = SystemClock.elapsedRealtime()
                isFirstFrameCaptured = true
                android.os.Trace.endSection() // End DroidLens:TimeToFirstFrame

                val ttff = firstFrameMs - onCreateEndMs
                Log.d(TAG, "First frame at: ${firstFrameMs}ms (TTFF: ${ttff}ms)")

                // Start time-to-interactive trace section
                android.os.Trace.beginSection("DroidLens:TimeToInteractive")

                emitMetrics()
            }
        }
    }

    /**
     * Called when the first user input event (MotionEvent.ACTION_DOWN) is detected.
     */
    internal fun markFirstInteractive() {
        if (!isInteractiveCaptured && isFirstFrameCaptured) {
            firstInteractiveMs = SystemClock.elapsedRealtime()
            isInteractiveCaptured = true
            android.os.Trace.endSection() // End DroidLens:TimeToInteractive
            android.os.Trace.endSection() // End DroidLens:TotalStartup

            val tti = firstInteractiveMs - firstFrameMs
            Log.d(TAG, "First interactive at: ${firstInteractiveMs}ms (TTI: ${tti}ms)")

            emitMetrics()
        }
    }

    /**
     * Emits the current state of startup metrics.
     */
    private fun emitMetrics() {
        val onCreateDuration = if (onCreateStartMs > 0 && onCreateEndMs > 0) {
            onCreateEndMs - onCreateStartMs
        } else 0L

        val ttff = if (isFirstFrameCaptured && onCreateEndMs > 0) {
            firstFrameMs - onCreateEndMs
        } else 0L

        val tti = if (isInteractiveCaptured && isFirstFrameCaptured) {
            firstInteractiveMs - firstFrameMs
        } else 0L

        val total = when {
            isInteractiveCaptured && processStartMs > 0 ->
                firstInteractiveMs - processStartMs
            isFirstFrameCaptured && processStartMs > 0 ->
                firstFrameMs - processStartMs
            onCreateEndMs > 0 && processStartMs > 0 ->
                onCreateEndMs - processStartMs
            else -> onCreateDuration
        }

        _metrics.value = StartupMetrics(
            applicationOnCreateMs = onCreateDuration,
            timeToFirstFrameMs = ttff,
            timeToInteractiveMs = tti,
            totalStartupMs = total,
            capturedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * Returns the current metrics snapshot (non-flow).
     */
    fun getMetricsSnapshot(): StartupMetrics {
        return _metrics.value ?: StartupMetrics.empty()
    }

    /**
     * Resets all timestamps for testing purposes.
     */
    internal fun reset() {
        processStartMs = 0L
        onCreateStartMs = 0L
        onCreateEndMs = 0L
        firstFrameMs = 0L
        firstInteractiveMs = 0L
        isFirstFrameCaptured = false
        isInteractiveCaptured = false
        _metrics.value = null
    }

    /**
     * Activity lifecycle callback that intercepts the first [MotionEvent]
     * via [Window.Callback] wrapping.
     */
    private class FirstInputCallback : Application.ActivityLifecycleCallbacks {
        private var isWrapped = false

        override fun onActivityResumed(activity: Activity) {
            if (!isWrapped && !isInteractiveCaptured) {
                wrapWindowCallback(activity)
                isWrapped = true
            }
        }

        private fun wrapWindowCallback(activity: Activity) {
            val originalCallback = activity.window.callback
            activity.window.callback = object : Window.Callback by originalCallback {
                override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
                    if (event?.action == MotionEvent.ACTION_DOWN) {
                        markFirstInteractive()
                        // Restore original callback to avoid overhead after first touch
                        activity.window.callback = originalCallback
                    }
                    return originalCallback.dispatchTouchEvent(event)
                }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
