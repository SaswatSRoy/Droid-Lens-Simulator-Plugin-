package com.droidlens.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidlens.R
import com.droidlens.model.FrameMetrics
import com.droidlens.model.HeapMetrics
import com.droidlens.model.StartupMetrics

// ── Colours ──
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val FpsGreen = Color(0xFF00E676)
private val FpsYellow = Color(0xFFFFD740)
private val FpsRed = Color(0xFFFF5252)
private val MemoryBarBg = Color(0xFF2A2A3E)
private val MemoryBarFill = Color(0xFF7C4DFF)
private val MemoryBarDanger = Color(0xFFFF5252)

/**
 * Top section of the expanded overlay showing real-time performance metrics.
 *
 * Row 1: FPS indicator (colour-coded) | Frame time in ms
 * Row 2: Memory used/max | Usage percentage bar
 */
@Composable
fun MetricsDashboard(
    frameMetrics: FrameMetrics,
    heapMetrics: HeapMetrics,
    startupMetrics: StartupMetrics? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Row 1: FPS & Frame Time ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FPS indicator
            Row(verticalAlignment = Alignment.Bottom) {
                val fpsColor = when {
                    frameMetrics.currentFps >= 50f -> FpsGreen
                    frameMetrics.currentFps >= 30f -> FpsYellow
                    else -> FpsRed
                }
                Text(
                    text = "${frameMetrics.currentFps.toInt()}",
                    color = fpsColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.fps_unit),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            // Frame time
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(frameMetrics.avgFrameTimeMs),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.ms_unit),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }

            // Jank count
            if (frameMetrics.jankFrameCount > 0) {
                Text(
                    text = "⚠ ${frameMetrics.jankFrameCount}",
                    color = FpsRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Row 2: Memory ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Memory text
            Text(
                text = "💾 ${"%.0f".format(heapMetrics.usedMemoryMb)}/${"%.0f".format(heapMetrics.maxMemoryMb)} ${stringResource(R.string.mb_unit)}",
                color = TextSecondary,
                fontSize = 11.sp
            )

            // Usage percentage
            Text(
                text = "${"%.0f".format(heapMetrics.usagePercent)}%",
                color = if (heapMetrics.usagePercent > 80f) FpsRed else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Memory usage bar
        MemoryBar(usagePercent = heapMetrics.usagePercent)

        // ── Row 3: Startup (only shown after metrics are captured) ──
        if (startupMetrics != null && startupMetrics.isCaptured) {
            Spacer(modifier = Modifier.height(6.dp))
            StartupRow(startupMetrics = startupMetrics)
        }
    }
}

/**
 * A thin horizontal bar showing memory usage as a percentage.
 */
@Composable
private fun MemoryBar(usagePercent: Float) {
    val fraction = (usagePercent / 100f).coerceIn(0f, 1f)
    val barColor = if (usagePercent > 80f) MemoryBarDanger else MemoryBarFill

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MemoryBarBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor)
        )
    }
}

/**
 * Compact row showing startup timing breakdown.
 * Shows: onCreate | TTFF | TTI | Total
 */
@Composable
private fun StartupRow(startupMetrics: StartupMetrics) {
    val startupColor = Color(0xFF80CBC4) // Teal accent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.startup_label),
            color = startupColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${startupMetrics.totalStartupMs}ms",
            color = when {
                startupMetrics.totalStartupMs > 1000 -> FpsRed
                startupMetrics.totalStartupMs > 500 -> FpsYellow
                else -> FpsGreen
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(2.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.init_label, startupMetrics.applicationOnCreateMs),
            color = TextSecondary,
            fontSize = 9.sp
        )
        if (startupMetrics.timeToFirstFrameMs > 0) {
            Text(
                text = stringResource(R.string.ttff_label, startupMetrics.timeToFirstFrameMs),
                color = TextSecondary,
                fontSize = 9.sp
            )
        }
        if (startupMetrics.timeToInteractiveMs > 0) {
            Text(
                text = stringResource(R.string.tti_label, startupMetrics.timeToInteractiveMs),
                color = TextSecondary,
                fontSize = 9.sp
            )
        }
    }
}
