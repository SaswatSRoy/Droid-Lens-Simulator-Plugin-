# 🔬 Droid Lens

> **Detect Android performance regressions on-device, explained in plain English — one Gradle dependency, zero configuration.**

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9%2B-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.06-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Status](https://img.shields.io/badge/status-alpha--0.1.0-orange.svg)]()

---

Droid Lens is an Android debug library that **attaches to your app in one line**, monitors performance in real time, and when something goes wrong — slow frames, memory leaks, Compose recomposition explosions, sluggish startup — it tells you **exactly what's wrong and how to fix it**, in plain English, using an on-device LLM. No Android Studio. No profiler. No guessing.

```
┌─────────────────────────────────┐
│  🔬 Droid Lens             ╌╌ ✕ │
├─────────────────────────────────┤
│  FPS: 24 🔴   Frame: 41.2ms     │
│  Mem: 187MB / 512MB  ████░░ 36% │
│  ▂▃▁▇████▆▃▂▁▃▄▇ ─ ─ 16ms      │
├─────────────────────────────────┤
│ 🐌 HIGH  Jank Frame             │
│  CAUSE: Layout inflation on     │
│  main thread in RecyclerView    │
│  FIX: Move to background with   │
│  AsyncLayoutInflater             │
│                      [Dismiss]  │
├─────────────────────────────────┤
│ 🔄 CRITICAL  Recomposition Spike│
│  "ProductCard" 47x/sec (3x base)│
│  ⏳ Analyzing...                 │
├─────────────────────────────────┤
│  🤖 AI Ready                    │
└─────────────────────────────────┘
```

---

## ✨ Features

- 🎯 **Zero-configuration setup** — one `init()` call, everything auto-instruments
- 🖼️ **Jank frame detection** — Choreographer-based frame timing with sub-millisecond precision, P95 tracking, consecutive jank counting
- 🔄 **Compose recomposition tracking** — detects composables recomposing far above their baseline; `Modifier.droidLensTracked()` for targeted monitoring
- 💾 **Memory leak detection** — WeakReference + ReferenceQueue ObjectWatcher pattern (inspired by LeakCanary) with Activity and Fragment auto-watching
- 🚀 **App startup tracing** — measures `Application.onCreate()`, time-to-first-frame, and time-to-interactive via Jetpack Startup
- 🧠 **On-device LLM diagnosis** — Gemma 2B INT4 via MediaPipe runs entirely on-device; your profiling data never leaves the phone
- 📊 **Floating overlay UI** — drag-repositionable Compose overlay rendered over WindowManager, live sparkline chart, per-issue explanation cards
- 📐 **Baseline comparison** — Room DB stores session baselines; regression detector uses sliding window comparison against your captured baseline
- 📄 **Session export** — JSON + Markdown reports with LLM-written summaries, share via FileProvider
- 🔒 **Debug-only** — use `debugImplementation` so zero library code ships to production users

---

## 📦 Installation

> **JitPack publishing is planned for v0.1.0 release (Phase 11).** Until then, clone and include the module directly.

### Option A — Local module (current)

```kotlin
// settings.gradle.kts
include(":droidlens-core")
project(":droidlens-core").projectDir = file("../DroidLens/droidlens-core")

// app/build.gradle.kts
debugImplementation(project(":droidlens-core"))
```

### Option B — JitPack (coming in v0.1.0)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
debugImplementation("com.github.saswatsroy:droidlens:0.1.0-alpha")
```

---

## ⚡ Quick Start

### 1. Initialise in your Application class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        DroidLens.init(this) {
            frameJankThreshold(16L)               // flag frames > 16ms
            recompositionSpikeMultiplier(3.0f)    // flag 3x above baseline
            memoryGrowthThreshold(50.0f)          // flag 50MB heap growth
            overlayPosition(OverlayPosition.TOP_RIGHT)
            llmEnabled(true)
            llmModelPath(filesDir.absolutePath + "/gemma-2b-it-cpu-int4.bin")
        }

        DroidLens.captureBaseline()   // snapshot current perf as "normal"
        DroidLens.showOverlay()       // show the floating overlay
    }
}
```

### 2. (Optional) Track specific composables

```kotlin
@Composable
fun ProductCard(product: Product) {
    Column(
        modifier = Modifier.droidLensTracked("ProductCard") // 👈 add this
    ) {
        Text(product.name)
        // ...
    }
}
```

### 3. End a session and get a report

```kotlin
val report: SessionReport = DroidLens.stopSession()
// report.detectedRegressions, report.llmDiagnoses, etc.
```

That's it. Everything else is automatic.

---

## ⚙️ Configuration Reference

All options have sensible defaults. Override only what you need.

| Option | Type | Default | Description |
|---|---|---|---|
| `frameJankThreshold` | `Long` (ms) | `16` | Frames exceeding this are flagged as jank |
| `recompositionSpikeMultiplier` | `Float` | `3.0` | Flag if recompositions exceed baseline × this |
| `memoryGrowthThreshold` | `Float` (MB) | `50.0` | Flag if heap grows by this much vs baseline |
| `startupRegressionThreshold` | `Long` (ms) | `200` | Flag startup slower than baseline by this |
| `overlayEnabled` | `Boolean` | `true` | Show/hide the floating overlay |
| `overlayPosition` | `OverlayPosition` | `TOP_RIGHT` | `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT` |
| `llmEnabled` | `Boolean` | `true` | Enable on-device LLM diagnosis (disable on low-RAM devices) |
| `llmModelPath` | `String` | `""` | Path to the Gemma 2B INT4 model file |
| `maxIssuesInOverlay` | `Int` | `5` | Max issue cards shown at once |
| `sessionName` | `String` | `"default"` | Tag used for baseline grouping |
| `exportEnabled` | `Boolean` | `false` | Auto-export JSON report on session end |
| `exportPath` | `String` | `""` | Output path for exports (defaults to `filesDir/droidlens/`) |

---

## 🧠 On-Device LLM Setup (Gemma 2B)

Droid Lens uses **Gemma 2B IT INT4** via MediaPipe Tasks GenAI — inference runs 100% on-device.

### Download the model

1. Visit [ai.google.dev/gemma](https://ai.google.dev/gemma) or [kaggle.com/models/google/gemma](https://www.kaggle.com/models/google/gemma)
2. Download `gemma-2b-it-cpu-int4.bin` (~1.3 GB)
3. Push to the device:
   ```bash
   adb push gemma-2b-it-cpu-int4.bin /data/local/tmp/
   ```
   Or use `ModelDownloader` to fetch it at runtime (see `LLMInferenceEngine.kt`)

### Point Droid Lens to it

```kotlin
DroidLens.init(this) {
    llmEnabled(true)
    llmModelPath("/data/local/tmp/gemma-2b-it-cpu-int4.bin")
}
```

### Device requirements

| RAM | Capability |
|---|---|
| ≥ 6 GB | Full LLM inference ✅ |
| 4–6 GB | May work; monitor for OOM |
| < 4 GB | Set `llmEnabled(false)` — rule-based fallbacks kick in automatically |

**No internet required.** All inference is local. Your profiling data never leaves the device.

### Fallback mode (no model)

If `llmEnabled = false` or the model file is missing, Droid Lens falls back to built-in rule-based diagnoses:

```
JankFrame     → "Frame exceeded render budget. Check for blocking operations
                  on the main thread. Consider LaunchedEffect for async work."

RecompositionSpike → "Excessive recomposition detected. Common causes: unstable
                       State, non-stable class parameters, lambda capture creating
                       new instances each composition."

MemoryLeak    → "Object retained after expected lifecycle end. Check for: static
                  references, unregistered listeners, inner class holding outer
                  class reference."
```

---

## 🏗️ Architecture

```
droidlens/
├── droidlens-core/                   # Main library module (AAR)
│   ├── instrumentation/
│   │   ├── FrameInstrumentation.kt   # Choreographer frame timing
│   │   ├── ComposeRecompositionTracker.kt  # Recomposition counting
│   │   ├── MemoryWatcher.kt          # Heap tracking + ObjectWatcher
│   │   └── StartupTracer.kt          # App startup phase timing
│   ├── analysis/
│   │   ├── BaselineManager.kt        # Room DB: store + retrieve baselines
│   │   ├── RegressionDetector.kt     # Sliding window comparison engine
│   │   └── AnomalyClassifier.kt      # Classifies regression type + severity
│   ├── llm/
│   │   ├── LLMInferenceEngine.kt     # MediaPipe LLM Inference wrapper
│   │   ├── PromptBuilder.kt          # Structured JSON → LLM prompt
│   │   └── DiagnosisResult.kt        # Sealed class for LLM output
│   ├── overlay/
│   │   ├── DroidLensOverlay.kt       # WindowManager floating overlay
│   │   ├── OverlayViewModel.kt       # State management for overlay UI
│   │   └── composables/
│   │       ├── MetricsDashboard.kt   # Live frame rate + memory graph
│   │       ├── IssueCard.kt          # Tappable issue with LLM explanation
│   │       └── SparklineChart.kt     # Lightweight real-time chart (Canvas)
│   ├── db/
│   │   ├── DroidLensDatabase.kt      # Room database definition
│   │   ├── BaselineDao.kt
│   │   ├── DetectedIssueDao.kt
│   │   └── entities/
│   │       ├── BaselineSnapshot.kt
│   │       └── DetectedIssue.kt
│   ├── config/
│   │   └── DroidLensConfig.kt        # Configuration DSL
│   └── DroidLens.kt                  # Public API entry point
└── app/                              # Demo + test application
    ├── MainActivity.kt
    ├── JankSimulator.kt              # Intentional jank frames for testing
    └── LeakSimulator.kt              # Intentional object leaks for testing
```

### Data flow

```
Choreographer ──► FrameInstrumentation ──► CircularFrameBuffer
                                                  │
ComposeModifier ──► RecompositionTracker          │
                                                  ▼
WeakReference ──► ObjectWatcher ──► MemoryWatcher
                                                  │
Jetpack Startup ──► StartupTracer                 │
                                                  ▼
                                         RegressionDetector
                                           (sliding window
                                            vs. baseline)
                                                  │
                                                  ▼
                                          LLMInferenceEngine
                                        (Gemma 2B / fallback)
                                                  │
                                                  ▼
                                          OverlayViewModel
                                           (StateFlow/Flow)
                                                  │
                                                  ▼
                                         DroidLensOverlay
                                       (WindowManager + Compose)
```

### Threading model

| Work | Thread |
|---|---|
| Choreographer frame callback | Main (< 0.1ms, immediately dispatched) |
| Frame analysis, regression detection | `Dispatchers.Default` |
| Room DB reads/writes | `Dispatchers.IO` |
| LLM inference | `Dispatchers.IO` (30s timeout) |
| ObjectWatcher GC checks | `Dispatchers.IO` |
| Overlay UI rendering | Main (Compose, excluded from measurements) |

---

## 📊 Public API

```kotlin
// Lifecycle
DroidLens.init(application, config)     // initialise; call in Application.onCreate()
DroidLens.startSession(name)            // begin a named profiling session
DroidLens.stopSession(): SessionReport  // end session, get report
DroidLens.isRunning(): Boolean

// Baseline management
DroidLens.captureBaseline()             // snapshot current metrics as "normal"
DroidLens.clearBaseline()

// Overlay
DroidLens.showOverlay()                 // requires DRAW_OVER_APPS permission
DroidLens.hideOverlay()

// Report
DroidLens.getSessionReport(): SessionReport
DroidLens.shareReport()                 // triggers system share sheet
```

### `SessionReport` fields

```kotlin
data class SessionReport(
    val sessionName: String,
    val durationMs: Long,
    val totalJankFrames: Int,
    val avgFrameTimeMs: Float,
    val recompositionSpikes: List<RecompositionSpike>,
    val memoryLeaks: List<MemoryLeak>,
    val detectedRegressions: List<Regression>,
    val llmDiagnoses: List<DiagnosisResult>,
    val exportedJsonPath: String?
)
```

### Regression severity thresholds

**Jank frames**

| Severity | Condition |
|---|---|
| `MEDIUM` | `frameTimeMs > threshold` (e.g. > 16ms) |
| `HIGH` | `frameTimeMs > threshold × 2` (e.g. > 32ms) |
| `CRITICAL` | `frameTimeMs > threshold × 3` (e.g. > 48ms) |

**Recomposition spikes**

| Severity | Condition |
|---|---|
| `MEDIUM` | count > baseline × 2 |
| `HIGH` | count > baseline × 3 |
| `CRITICAL` | count > baseline × 5 |

**Memory growth**

| Severity | Condition |
|---|---|
| `MEDIUM` | growth > threshold |
| `HIGH` | growth > threshold × 1.5 |
| `CRITICAL` | growth > threshold × 2 |

---

## 🧪 Testing & Demo App

The included `app/` module has simulators for every regression type:

```kotlin
// JankSimulator.kt
Button("Simulate Jank")          // blocks main thread 50ms once
Button("Simulate Sustained Jank")// blocks 20ms/frame for 3 seconds

// LeakSimulator.kt
Button("Leak an Activity")       // static reference to finished Activity
Button("Leak a Coroutine Scope") // CoroutineScope in composable, never cancelled
```

### Run unit tests

```bash
./gradlew :droidlens-core:testDebugUnitTest
```

### Run instrumented tests

```bash
./gradlew :droidlens-core:connectedAndroidTest
```

### Full build verification

```bash
./gradlew clean build
./gradlew :droidlens-core:lint
./gradlew :droidlens-core:assembleRelease   # produces AAR
```

---

## 📋 Requirements

| Requirement | Value |
|---|---|
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 |
| Kotlin | 1.9+ |
| Gradle | 8.x |
| Compose BOM | 2024.06.00 |
| LLM model RAM | ≥ 6 GB recommended |

---

## 🗺️ Roadmap

- [x] Phase 1 — Project scaffold + Gradle setup
- [x] Phase 2 — Configuration DSL + public API
- [x] Phase 3 — Frame instrumentation (Choreographer)
- [x] Phase 4 — Compose recomposition tracking
- [x] Phase 5 — Memory leak detection (ObjectWatcher)
- [x] Phase 6 — Room DB baseline storage + regression detector
- [x] Phase 7 — On-device LLM inference (MediaPipe + Gemma 2B)
- [x] Phase 8 — Floating overlay UI (Compose + WindowManager)
- [x] Phase 9 — App startup tracer (Jetpack Startup)
- [x] Phase 10 — Session export (JSON + Markdown reports)
- [ ] Phase 11 — JitPack publishing (`com.github.saswatsroy:droidlens:0.1.0-alpha`)
- [ ] Phase 12 — Optional Spring Boot backend (team dashboard, Kafka CI alerts)

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Follow the existing code style — KDoc on every public function, `try-catch` or sealed-class result on every coroutine and IO call
4. Write unit tests for any new logic (use `TestCoroutineDispatcher` for coroutine tests)
5. Run `./gradlew clean build lint testDebugUnitTest` — all must pass
6. Submit a pull request with a clear description

### Code conventions

- All async work via Kotlin Coroutines + Flow — no `AsyncTask`, no `Thread`
- Main thread frame callback overhead must stay under 0.1ms
- No new external dependencies without discussion — the library keeps its footprint lean
- `Dispatchers.Default` for CPU-bound analysis, `Dispatchers.IO` for DB and file work

---

## 📜 License

```
Copyright 2024 Saswat Suman Roy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 👤 Author

**Saswat Suman Roy**
NIT Rourkela

---

*Droid Lens — because your users shouldn't be your profiler.*
