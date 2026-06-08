package com.droidlens.overlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.droidlens.overlay.OverlayViewModel

// ── DroidLens theme colours ──
private val SurfaceDark = Color(0xFF1A1A2E)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)

/**
 * Root composable for the floating overlay.
 *
 * Toggles between:
 * - **Minimised**: A small 48dp glowing circle (tap to expand)
 * - **Expanded**: Full metrics dashboard + issue cards
 */
@Composable
fun DroidLensOverlayContent(
    viewModel: OverlayViewModel,
    jankThresholdMs: Long,
    onClose: () -> Unit
) {
    val isMinimised by viewModel.isMinimised.collectAsState()
    val frameMetrics by viewModel.frameMetrics.collectAsState()

    // Minimised dot
    AnimatedVisibility(
        visible = isMinimised,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        MinimisedDot(
            isJanking = frameMetrics.isJanking,
            fps = frameMetrics.currentFps
        )
    }

    // Expanded overlay
    AnimatedVisibility(
        visible = !isMinimised,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
    ) {
        ExpandedOverlay(
            viewModel = viewModel,
            jankThresholdMs = jankThresholdMs,
            onClose = onClose
        )
    }
}

/**
 * The minimised state: a small coloured dot showing at-a-glance health.
 * Green = healthy (>50fps), Yellow = degraded (30-50fps), Red = janking (<30fps).
 */
@Composable
private fun MinimisedDot(
    isJanking: Boolean,
    fps: Float
) {
    val dotColor = when {
        isJanking || fps < 30f -> AccentRed
        fps < 50f -> Color(0xFFFFD740)
        else -> AccentGreen
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SurfaceDark.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

/**
 * The expanded overlay with metrics dashboard, sparkline, and issue cards.
 */
@Composable
private fun ExpandedOverlay(
    viewModel: OverlayViewModel,
    jankThresholdMs: Long,
    onClose: () -> Unit
) {
    val frameMetrics by viewModel.frameMetrics.collectAsState()
    val heapMetrics by viewModel.heapMetrics.collectAsState()
    val recentIssues by viewModel.recentIssues.collectAsState()
    val llmStatus by viewModel.llmStatus.collectAsState()
    val frameHistory by viewModel.frameHistory.collectAsState()
    val startupMetrics by viewModel.startupMetrics.collectAsState()

    Column(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark.copy(alpha = 0.85f))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // LLM Status indicator
        LlmStatusIndicator(status = llmStatus)

        Spacer(modifier = Modifier.height(8.dp))

        // Metrics Dashboard (FPS, frame time, memory, startup)
        MetricsDashboard(
            frameMetrics = frameMetrics,
            heapMetrics = heapMetrics,
            startupMetrics = startupMetrics
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sparkline chart (last 60 frame durations)
        if (frameHistory.isNotEmpty()) {
            SparklineChart(
                data = frameHistory,
                thresholdMs = jankThresholdMs.toFloat()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Issue cards
        recentIssues.forEach { issue ->
            IssueCard(
                issue = issue,
                onDismiss = { viewModel.dismissIssue(issue.id) }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
