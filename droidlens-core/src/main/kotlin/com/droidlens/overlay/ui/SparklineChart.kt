package com.droidlens.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ── Colours ──
private val ChartBackground = Color(0xFF12121F)
private val LineColor = Color(0xFF7C4DFF)
private val ThresholdLineColor = Color(0xFFFF5252)
private val GlowColor = Color(0x407C4DFF)

/**
 * A mini sparkline chart that draws the last 60 frame durations.
 *
 * Features:
 * - Pure Canvas drawing (no external chart library)
 * - Red threshold line at [thresholdMs]
 * - Smooth path connecting data points
 * - Subtle glow fill under the line
 *
 * @param data List of frame duration values in milliseconds
 * @param thresholdMs The jank threshold in ms (drawn as a red dashed line)
 */
@Composable
fun SparklineChart(
    data: List<Float>,
    thresholdMs: Float,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ChartBackground)
    ) {
        val width = size.width
        val height = size.height
        val padding = 2.dp.toPx()

        val drawWidth = width - padding * 2
        val drawHeight = height - padding * 2

        // Calculate Y scale — use max of (data max, threshold * 2) to keep threshold visible
        val maxValue = maxOf(data.max(), thresholdMs * 2f)
        val minValue = 0f

        fun valueToY(value: Float): Float {
            val normalized = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
            return padding + drawHeight * (1f - normalized)
        }

        fun indexToX(index: Int): Float {
            return if (data.size <= 1) {
                padding + drawWidth / 2
            } else {
                padding + (index.toFloat() / (data.size - 1)) * drawWidth
            }
        }

        // ── Draw threshold line ──
        val thresholdY = valueToY(thresholdMs)
        val dashWidth = 4.dp.toPx()
        val gapWidth = 3.dp.toPx()
        var dashX = padding
        while (dashX < width - padding) {
            val endX = minOf(dashX + dashWidth, width - padding)
            drawLine(
                color = ThresholdLineColor.copy(alpha = 0.5f),
                start = Offset(dashX, thresholdY),
                end = Offset(endX, thresholdY),
                strokeWidth = 1.dp.toPx()
            )
            dashX += dashWidth + gapWidth
        }

        if (data.size < 2) return@Canvas

        // ── Build line path ──
        val linePath = Path().apply {
            moveTo(indexToX(0), valueToY(data[0]))
            for (i in 1 until data.size) {
                lineTo(indexToX(i), valueToY(data[i]))
            }
        }

        // ── Build fill path (area under line) ──
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(indexToX(data.size - 1), padding + drawHeight)
            lineTo(indexToX(0), padding + drawHeight)
            close()
        }

        // ── Draw glow fill ──
        drawPath(
            path = fillPath,
            color = GlowColor
        )

        // ── Draw line ──
        drawPath(
            path = linePath,
            color = LineColor,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // ── Draw latest point dot ──
        val lastX = indexToX(data.size - 1)
        val lastY = valueToY(data.last())
        val dotColor = if (data.last() > thresholdMs) ThresholdLineColor else LineColor

        drawCircle(
            color = dotColor,
            radius = 2.5.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}
