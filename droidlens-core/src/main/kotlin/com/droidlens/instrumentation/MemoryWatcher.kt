package com.droidlens.instrumentation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.droidlens.model.HeapMetrics
import com.droidlens.model.Regression
import com.droidlens.model.RegressionType
import com.droidlens.model.Severity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks overall heap metrics and integrates [ObjectWatcher] with lifecycle hooks.
 */
class MemoryWatcher(
    private val objectWatcher: ObjectWatcher,
    private val memoryGrowthThresholdMb: Float = 50f,
    private val sessionName: String = "default",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : Application.ActivityLifecycleCallbacks {

    private val _heapMetrics = MutableStateFlow(HeapMetrics.empty())
    val heapMetrics = _heapMetrics.asStateFlow()

    private val _memoryRegressions = MutableSharedFlow<Regression>(extraBufferCapacity = 10)
    val memoryRegressions = _memoryRegressions.asSharedFlow()

    private var baselineMemoryMb: Float = -1f
    private var trackingJob: Job? = null

    fun start(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        objectWatcher.start()
        
        trackingJob = scope.launch {
            while (isActive) {
                updateHeapMetrics()
                delay(2000)
            }
        }

        // Initialize baseline after a short delay
        scope.launch {
            delay(5000)
            baselineMemoryMb = getCurrentUsedMemoryMb()
            Log.d("DroidLens-Memory", "Baseline memory set: $baselineMemoryMb MB")
        }
    }

    fun stop(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
        objectWatcher.stop()
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun updateHeapMetrics() {
        val used = getCurrentUsedMemoryMb()
        val max = Runtime.getRuntime().maxMemory() / 1024f / 1024f
        val percent = (used / max) * 100f
        
        val metrics = HeapMetrics(
            usedMemoryMb = used,
            maxMemoryMb = max,
            usagePercent = percent,
            watchedObjectCount = objectWatcher.getWatchedCount(),
            retainedObjectCount = objectWatcher.getRetainedCount()
        )
        _heapMetrics.value = metrics

        checkMemoryGrowth(used)
    }

    private fun checkMemoryGrowth(currentUsedMb: Float) {
        if (baselineMemoryMb > 0 && currentUsedMb - baselineMemoryMb > memoryGrowthThresholdMb) {
            val regression = Regression(
                type = RegressionType.MemoryLeak(
                    retainedObjectClass = "Large Heap Growth",
                    retainedSizeBytes = ((currentUsedMb - baselineMemoryMb) * 1024 * 1024).toLong()
                ),
                severity = Severity.HIGH,
                detectedAtMs = System.currentTimeMillis(),
                sessionName = sessionName,
                rawData = "{\"usedMb\": $currentUsedMb, \"baselineMb\": $baselineMemoryMb}"
            )
            _memoryRegressions.tryEmit(regression)
            Log.w("DroidLens-Memory", "MEMORY REGRESSION: Heap grew by ${currentUsedMb - baselineMemoryMb} MB")
            
            // Update baseline to avoid spamming the same regression
            baselineMemoryMb = currentUsedMb
        }
    }

    private fun getCurrentUsedMemoryMb(): Float {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024f / 1024f
    }

    // ActivityLifecycleCallbacks

    override fun onActivityDestroyed(activity: Activity) {
        objectWatcher.watch(activity, "Activity: ${activity.javaClass.simpleName}")
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                object : FragmentManager.FragmentLifecycleCallbacks() {
                    override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                        objectWatcher.watch(f, "Fragment: ${f.javaClass.simpleName}")
                    }
                }, true
            )
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
