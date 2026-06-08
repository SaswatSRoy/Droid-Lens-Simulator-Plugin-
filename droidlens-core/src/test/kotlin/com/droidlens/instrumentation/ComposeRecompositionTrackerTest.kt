package com.droidlens.instrumentation

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ComposeRecompositionTrackerTest {

    @Before
    fun setup() {
        ComposeRecompositionTracker.reset()
    }

    @Test
    fun `test recording and snapshot`() {
        ComposeRecompositionTracker.recordRecomposition("ComposableA")
        ComposeRecompositionTracker.recordRecomposition("ComposableA")
        ComposeRecompositionTracker.recordRecomposition("ComposableB")

        val snapshot = ComposeRecompositionTracker.getRecompositionSnapshot()
        
        assertEquals(2, snapshot["ComposableA"])
        assertEquals(1, snapshot["ComposableB"])
        assertEquals(2, snapshot.size)
    }

    @Test
    fun `test reset`() {
        ComposeRecompositionTracker.recordRecomposition("ComposableA")
        ComposeRecompositionTracker.reset()
        
        val snapshot = ComposeRecompositionTracker.getRecompositionSnapshot()
        assertEquals(0, snapshot["ComposableA"])
    }
}
