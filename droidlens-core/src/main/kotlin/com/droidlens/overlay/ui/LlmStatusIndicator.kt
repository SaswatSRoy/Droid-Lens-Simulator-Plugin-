package com.droidlens.overlay.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidlens.R
import com.droidlens.overlay.LlmStatus

// ── Colours ──
private val StatusGreen = Color(0xFF00E676)
private val StatusYellow = Color(0xFFFFD740)
private val StatusGrey = Color(0xFF757575)
private val ChipBackground = Color(0xFF222238)

/**
 * Small status chip showing the current LLM engine state.
 *
 * States:
 * - 🤖 Ready (green)
 * - ⏳ Loading... (yellow, static)
 * - ⏳ Analyzing... (yellow, pulsing animation)
 * - ⚠️ AI Unavailable (grey)
 */
@Composable
fun LlmStatusIndicator(
    status: LlmStatus,
    modifier: Modifier = Modifier
) {
    val (icon, labelRes, color) = when (status) {
        LlmStatus.READY -> Triple("🤖", R.string.ai_ready, StatusGreen)
        LlmStatus.LOADING -> Triple("⏳", R.string.ai_loading, StatusYellow)
        LlmStatus.PROCESSING -> Triple("⏳", R.string.ai_analyzing, StatusYellow)
        LlmStatus.UNAVAILABLE -> Triple("⚠️", R.string.ai_unavailable, StatusGrey)
    }

    // Pulsing animation for PROCESSING state
    val alpha = if (status == LlmStatus.PROCESSING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        pulseAlpha
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ChipBackground)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(labelRes),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
