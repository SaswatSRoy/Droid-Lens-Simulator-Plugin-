package com.droidlens.app.simulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droidlens.DroidLens

import androidx.compose.ui.res.stringResource
import com.droidlens.app.R

// Static list to cause leaks
private val leakedActivities = mutableListOf<Activity>()

class LeakActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Add to static list to cause a leak
        leakedActivities.add(this)
        
        setContent {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.activity_leaked))
                    Button(onClick = { finish() }) {
                        Text("Finish Activity")
                    }
                }
            }
        }
    }
}

@Composable
fun LeakSimulator(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val heapMetrics by DroidLens.getHeapMetrics().collectAsState()

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stringResource(R.string.memory_leak_tracking), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        Text(text = stringResource(R.string.memory_usage_label, heapMetrics.usedMemoryMb, heapMetrics.maxMemoryMb))
        Text(text = stringResource(R.string.usage_percent_label, heapMetrics.usagePercent))
        Text(text = stringResource(R.string.watched_retained_label, heapMetrics.watchedObjectCount, heapMetrics.retainedObjectCount))

        Button(onClick = {
            val intent = Intent(context, LeakActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Launch & Leak Activity")
        }
        
        Button(onClick = {
            leakedActivities.clear()
            Runtime.getRuntime().gc()
        }) {
            Text("Clear Leaks & GC")
        }

        Button(onClick = {
            Runtime.getRuntime().gc()
        }) {
            Text("Trigger Manual GC")
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}
