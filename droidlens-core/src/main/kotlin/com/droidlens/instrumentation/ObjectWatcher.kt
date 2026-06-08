package com.droidlens.instrumentation

import android.util.Log
import com.droidlens.model.MemoryLeakEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Watches objects to detect if they are retained after they should have been GC'd.
 * Inspired by LeakCanary's ObjectWatcher.
 */
class ObjectWatcher(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val watchedObjects = ConcurrentHashMap<String, KeyedWeakReference>()
    private val queue = ReferenceQueue<Any>()
    
    private val _leakEvents = MutableSharedFlow<MemoryLeakEvent>(extraBufferCapacity = 20)
    val leakEvents = _leakEvents.asSharedFlow()

    private var watchJob: Job? = null

    /**
     * Starts the background watching process.
     */
    fun start() {
        if (watchJob != null) return
        watchJob = scope.launch {
            while (isActive) {
                delay(5000)
                checkRetainedObjects()
            }
        }
    }

    /**
     * Stops the background watching process.
     */
    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * Watches an object for potential leaks.
     * @return A unique watch key.
     */
    fun watch(watchedObject: Any, description: String): String {
        removeClearedReferences()
        val key = UUID.randomUUID().toString()
        val watchTime = System.currentTimeMillis()
        val reference = KeyedWeakReference(watchedObject, key, description, watchTime, queue)
        watchedObjects[key] = reference
        Log.v("DroidLens-Memory", "Watching: $description ($key)")
        return key
    }

    /**
     * Explicitly removes an object from being watched.
     */
    fun clearWatch(watchKey: String) {
        watchedObjects.remove(watchKey)
    }

    /**
     * Clears all watched objects.
     */
    fun clearAll() {
        watchedObjects.clear()
    }

    /**
     * Returns the count of objects currently being watched.
     */
    fun getWatchedCount(): Int = watchedObjects.size

    /**
     * Returns the count of objects that are currently considered retained (leaked).
     */
    fun getRetainedCount(): Int {
        val now = System.currentTimeMillis()
        return watchedObjects.values.count { it.isRetained(now) }
    }

    private suspend fun checkRetainedObjects() {
        removeClearedReferences()
        
        val now = System.currentTimeMillis()
        val retained = watchedObjects.values.filter { it.isRetained(now) }
        
        if (retained.isNotEmpty()) {
            // Trigger GC to reduce false positives
            Log.d("DroidLens-Memory", "Potential leaks detected, triggering GC...")
            Runtime.getRuntime().gc()
            
            // Wait for GC to settle
            delay(2000)
            removeClearedReferences()
            
            val stillRetained = watchedObjects.values.filter { it.isRetained(now) }
            stillRetained.forEach { ref ->
                val event = MemoryLeakEvent(
                    watchKey = ref.key,
                    description = ref.description,
                    retainedSince = ref.watchTimeMs,
                    estimatedRetainedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() // Simple delta
                )
                _leakEvents.tryEmit(event)
                Log.w("DroidLens-Memory", "OBJECT LEAKED: ${ref.description} (${ref.key})")
            }
        }
    }

    private fun removeClearedReferences() {
        var ref = queue.poll()
        while (ref != null) {
            if (ref is KeyedWeakReference) {
                watchedObjects.remove(ref.key)
                Log.v("DroidLens-Memory", "Cleared: ${ref.description} (${ref.key})")
            }
            ref = queue.poll()
        }
    }

    /**
     * WeakReference that holds metadata about the watched object.
     */
    private class KeyedWeakReference(
        referent: Any,
        val key: String,
        val description: String,
        val watchTimeMs: Long,
        referenceQueue: ReferenceQueue<Any>
    ) : WeakReference<Any>(referent, referenceQueue) {
        
        fun isRetained(now: Long): Boolean {
            // Considered retained if it hasn't been cleared 5s after watching
            return get() != null && (now - watchTimeMs) > 5000
        }
    }
}
