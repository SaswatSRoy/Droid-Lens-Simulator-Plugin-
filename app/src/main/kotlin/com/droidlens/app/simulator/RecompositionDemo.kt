package com.droidlens.app.simulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droidlens.instrumentation.ComposeRecompositionTracker
import com.droidlens.instrumentation.droidLensTracked
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

import androidx.compose.ui.res.stringResource
import com.droidlens.app.R

@Composable
fun RecompositionDemo(modifier: Modifier = Modifier) {
    var ticker by remember { mutableIntStateOf(0) }
    var rapidCounter by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val currentCount = ComposeRecompositionTracker.getRecompositionCount("RecompositionDemo.Counter")

    LaunchedEffect(Unit) {
        while (true) {
            delay(100.milliseconds)
            ticker++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stringResource(R.string.recomposition_tracking), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        
        Column(
            modifier = Modifier.droidLensTracked("RecompositionDemo.Counter"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.ticker_label, ticker))
            Text(text = stringResource(R.string.rapid_counter_label, rapidCounter))
            Text(text = stringResource(R.string.tracked_count_label, currentCount))
        }

        Button(onClick = {
            scope.launch {
                repeat(50) { 
                    rapidCounter++ 
                    delay(16) // Delay 1 frame to prevent Compose state batching
                }
            }
        }) {
            Text("Trigger 50 Recompositions")
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}
