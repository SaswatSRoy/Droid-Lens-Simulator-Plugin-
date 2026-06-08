package com.droidlens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.droidlens.app.simulator.JankSimulator
import com.droidlens.app.simulator.LeakSimulator
import com.droidlens.app.simulator.RecompositionDemo
import com.droidlens.app.simulator.StartupSimulator
import com.droidlens.app.ui.theme.DroidLensTheme
import com.droidlens.DroidLens

import androidx.compose.ui.res.stringResource

/**
 * Demo activity for the Droid Lens library.
 * This app exists solely to test and demonstrate the droidlens-core library
 * on a real device. It is NOT part of the library itself.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidLensTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DemoScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.demo_app_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            SessionExportControls()
        }
        item {
            StartupSimulator()
        }
        item {
            JankSimulator()
        }
        item {
            RecompositionDemo()
        }
        item {
            LeakSimulator()
        }
    }
}

@Composable
fun SessionExportControls() {
    val context = LocalContext.current
    var hasExported by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Session Actions", style = MaterialTheme.typography.titleMedium)
        
        Button(onClick = {
            DroidLens.stopSession()
            hasExported = true
        }) {
            Text("Stop Session & Export")
        }

        if (hasExported) {
            Button(onClick = {
                DroidLens.shareReport(context)
            }) {
                Text("Share Report")
            }
            
            Button(onClick = {
                DroidLens.startSession("demo-startup-${System.currentTimeMillis()}")
                hasExported = false
            }) {
                Text("Start New Session")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DemoScreenPreview() {
    DroidLensTheme {
        DemoScreen()
    }
}
