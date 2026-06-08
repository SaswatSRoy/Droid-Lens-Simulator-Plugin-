package com.droidlens.app.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidlens.DroidLens

import androidx.compose.ui.res.stringResource
import com.droidlens.app.R

/**
 * Demo UI for displaying startup metrics and simulating slow startup.
 *
 * Shows the current startup timing breakdown and provides a button
 * to inject a 500ms delay into a re-initialised startup measurement
 * to test regression detection.
 */
@Composable
fun StartupSimulator(modifier: Modifier = Modifier) {
    val startupMetrics by DroidLens.getStartupMetrics().collectAsState()

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.startup_metrics_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        startupMetrics?.let { metrics ->
            if (metrics.isCaptured) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.app_on_create))
                    Text(
                        text = "${metrics.applicationOnCreateMs}ms",
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.ttff))
                    Text(
                        text = if (metrics.timeToFirstFrameMs > 0) "${metrics.timeToFirstFrameMs}ms" else "...",
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = stringResource(R.string.tti))
                    Text(
                        text = if (metrics.timeToInteractiveMs > 0) "${metrics.timeToInteractiveMs}ms" else stringResource(R.string.waiting_touch),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.total_startup),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${metrics.totalStartupMs}ms",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = when {
                            metrics.totalStartupMs > 1000 -> Color.Red
                            metrics.totalStartupMs > 500 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }
            } else {
                Text(text = stringResource(R.string.startup_not_captured))
            }
        } ?: run {
            Text(text = stringResource(R.string.startup_not_available))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                // Simulate a slow init by blocking the main thread for 500ms
                // This will cause a startup regression on next app launch
                Thread.sleep(500)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF9800)
            )
        ) {
            Text(stringResource(R.string.simulate_slow_init))
        }
    }
}
