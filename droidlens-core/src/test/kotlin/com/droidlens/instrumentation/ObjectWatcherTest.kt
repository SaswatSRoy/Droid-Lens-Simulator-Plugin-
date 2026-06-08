package com.droidlens.instrumentation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ObjectWatcherTest {

    @Test
    fun `test watch increases count`() {
        val watcher = ObjectWatcher()
        val obj = Any()
        
        watcher.watch(obj, "Test Object")
        
        assertEquals(1, watcher.getWatchedCount())
    }

    @Test
    fun `test clearWatch decreases count`() {
        val watcher = ObjectWatcher()
        val obj = Any()
        val key = watcher.watch(obj, "Test Object")
        
        watcher.clearWatch(key)
        
        assertEquals(0, watcher.getWatchedCount())
    }

    @Test
    fun `test clearAll clears everything`() {
        val watcher = ObjectWatcher()
        watcher.watch(Any(), "Obj 1")
        watcher.watch(Any(), "Obj 2")
        
        watcher.clearAll()
        
        assertEquals(0, watcher.getWatchedCount())
    }
}
