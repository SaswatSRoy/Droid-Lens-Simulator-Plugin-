package com.droidlens.app.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidlens.DroidLens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.res.stringResource
import com.droidlens.app.R

@Composable
fun JankSimulator(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val metrics by DroidLens.getFrameMetrics().collectAsState()

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.fps_label, metrics.currentFps.toInt()))
        Text(text = stringResource(R.string.avg_frame_label, metrics.avgFrameTimeMs))
        Text(text = stringResource(R.string.p95_frame_label, metrics.p95FrameTimeMs))
        Text(text = stringResource(R.string.jank_frames_label, metrics.jankFrameCount))

        Button(onClick = {
            // Intentionally block main thread for 50ms
            Thread.sleep(50)
        }) {
            Text(stringResource(R.string.simulate_jank))
        }

        Button(onClick = {
            scope.launch {
                val endTime = System.currentTimeMillis() + 3000
                while (System.currentTimeMillis() < endTime) {
                    // Block for 20ms every "frame" loop
                    Thread.sleep(20)
                    delay(5) // Yield to allow some rendering
                }
            }
        }) {
            Text(stringResource(R.string.simulate_sustained_jank))
        }
    }
}
