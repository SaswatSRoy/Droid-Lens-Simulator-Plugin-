package com.droidlens.instrumentation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CircularFrameBufferTest {

    @Test
    fun `test buffer write and readAll`() {
        val instrumentation = FrameInstrumentation()
        val buffer = instrumentation.CircularFrameBuffer(3)
        
        buffer.write(10L)
        buffer.write(20L)
        
        assertArrayEquals(longArrayOf(10L, 20L), buffer.readAll())
        
        buffer.write(30L)
        assertArrayEquals(longArrayOf(10L, 20L, 30L), buffer.readAll())
        
        buffer.write(40L)
        assertArrayEquals(longArrayOf(20L, 30L, 40L), buffer.readAll())
    }

    @Test
    fun `test average and percentile`() {
        val instrumentation = FrameInstrumentation()
        val buffer = instrumentation.CircularFrameBuffer(10)
        
        for (i in 1..10) {
            buffer.write(i * 1_000_000L) // 1ms, 2ms, ..., 10ms
        }
        
        assertEquals(5.5f, buffer.average(), 0.01f)
        assertEquals(9.0f, buffer.percentile(0.95f), 0.5f) // 95th percentile of 10 items (index 8)
        assertEquals(5.0f, buffer.percentile(0.5f), 0.5f)  // 50th percentile (index 4)
    }

    @Test
    fun `test clear`() {
        val instrumentation = FrameInstrumentation()
        val buffer = instrumentation.CircularFrameBuffer(5)
        
        buffer.write(100L)
        buffer.clear()
        
        assertEquals(0, buffer.readAll().size)
        assertEquals(0f, buffer.average(), 0f)
    }
}
