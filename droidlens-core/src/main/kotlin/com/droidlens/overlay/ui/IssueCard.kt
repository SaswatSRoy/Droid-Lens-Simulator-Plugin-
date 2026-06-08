package com.droidlens.overlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidlens.R
import com.droidlens.db.entities.DetectedIssue

// ── Colours ──
private val CardBackground = Color(0xFF222238)
private val SeverityCritical = Color(0xFFFF5252)
private val SeverityHigh = Color(0xFFFF9800)
private val SeverityMedium = Color(0xFFFFD740)
private val TextPrimary = Color(0xFFE0E0E0)
private val TextSecondary = Color(0xFF9E9E9E)
private val TextSuggestion = Color(0xFF80CBC4)
private val DismissColor = Color(0xFF7C4DFF)

/**
 * A tappable issue card displayed in the overlay.
 *
 * Features:
 * - Severity colour indicator bar on the left edge
 * - Type icon (emoji: 🐌 jank, 🔄 recompose, 💾 memory)
 * - LLM explanation text (or "Analyzing..." if pending)
 * - LLM suggestion text (shown when expanded)
 * - Expand/collapse animation for full text
 * - Dismiss button
 *
 * @param issue The detected issue from the database
 * @param onDismiss Callback to mark the issue as resolved
 */
@Composable
fun IssueCard(
    issue: DetectedIssue,
    onDismiss: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val severityColor = when (issue.severity) {
        "CRITICAL" -> SeverityCritical
        "HIGH" -> SeverityHigh
        "MEDIUM" -> SeverityMedium
        else -> TextSecondary
    }

    val typeIcon = when {
        issue.regressionType.contains("Jank", ignoreCase = true) -> "🐌"
        issue.regressionType.contains("Recomposition", ignoreCase = true) -> "🔄"
        issue.regressionType.contains("Memory", ignoreCase = true) -> "💾"
        issue.regressionType.contains("Startup", ignoreCase = true) -> "🚀"
        else -> "📊"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBackground)
            .clickable { isExpanded = !isExpanded }
    ) {
        // Severity indicator bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (isExpanded) 80.dp else 40.dp)
                .background(severityColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Header row: icon + type name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = typeIcon,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatTypeName(issue.regressionType),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Dismiss button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dismiss_icon),
                        color = DismissColor,
                        fontSize = 12.sp
                    )
                }
            }

            // Explanation text
            val explanation = issue.llmExplanation ?: stringResource(R.string.ai_loading)
            Text(
                text = explanation,
                color = if (issue.llmExplanation != null) TextSecondary else Color(0xFFFFD740),
                fontSize = 10.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )

            // Expanded content: suggestion
            AnimatedVisibility(
                visible = isExpanded && issue.llmSuggestion != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.llm_suggestion_prefix, issue.llmSuggestion ?: ""),
                        color = TextSuggestion,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Formats the regression type class name into a human-readable label.
 */
@Composable
private fun formatTypeName(typeName: String): String {
    return when {
        typeName.contains("JankFrame") -> stringResource(R.string.type_jank)
        typeName.contains("RecompositionSpike") -> stringResource(R.string.type_recomposition)
        typeName.contains("MemoryLeak") -> stringResource(R.string.type_leak)
        typeName.contains("SlowStartup") -> stringResource(R.string.type_startup)
        else -> typeName
    }
}
