package com.droidlens.model

/**
 * Metrics captured during app startup, broken into phases.
 *
 * @param applicationOnCreateMs Duration of Application.onCreate()
 * @param timeToFirstFrameMs Time from Application.onCreate() end to first Choreographer callback
 * @param timeToInteractiveMs Time from first frame to first user MotionEvent
 * @param totalStartupMs Total startup time from process start to interactive
 * @param capturedAtMs Wall-clock time when these metrics were captured
 */
data class StartupMetrics(
    val applicationOnCreateMs: Long = 0L,
    val timeToFirstFrameMs: Long = 0L,
    val timeToInteractiveMs: Long = 0L,
    val totalStartupMs: Long = 0L,
    val capturedAtMs: Long = 0L
) {
    /**
     * True when at least Application.onCreate() has been measured.
     */
    val isCaptured: Boolean
        get() = applicationOnCreateMs > 0 || totalStartupMs > 0

    companion object {
        fun empty() = StartupMetrics()
    }
}
