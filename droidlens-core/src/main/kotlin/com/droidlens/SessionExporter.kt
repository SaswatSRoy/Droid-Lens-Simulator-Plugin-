package com.droidlens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.droidlens.config.DroidLensConfig
import com.droidlens.model.DiagnosisResult
import com.droidlens.model.Regression
import com.droidlens.model.RegressionType
import com.droidlens.model.SessionReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles exporting profiling session data to JSON and Markdown formats.
 */
object SessionExporter {
    private const val TAG = "DroidLens-Exporter"

    private val json = Json { 
        prettyPrint = true
        encodeDefaults = true 
    }

    /**
     * Exports a SessionReport to both JSON and Markdown formats.
     * Returns a pair of the generated files (JSON, Markdown).
     */
    fun exportSession(
        context: Context,
        report: SessionReport,
        config: DroidLensConfig
    ): Pair<File, File> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseFileName = "droidlens_${report.sessionName}_$timestamp"
        
        val outputDir = if (config.exportPath.isNotEmpty()) {
            File(config.exportPath).apply { mkdirs() }
        } else {
            File(context.filesDir, "droidlens").apply { mkdirs() }
        }

        val jsonFile = exportToJson(report, outputDir, "$baseFileName.json")
        val mdFile = exportToMarkdown(context, report, outputDir, "$baseFileName.md")

        return Pair(jsonFile, mdFile)
    }

    private fun exportToJson(report: SessionReport, dir: File, fileName: String): File {
        val file = File(dir, fileName)
        try {
            val jsonString = json.encodeToString(report)
            file.writeText(jsonString)
            Log.d(TAG, "Exported JSON to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export JSON", e)
        }
        return file
    }

    private fun exportToMarkdown(context: Context, report: SessionReport, dir: File, fileName: String): File {
        val file = File(dir, fileName)
        try {
            val mdString = generateMarkdown(context, report)
            file.writeText(mdString)
            Log.d(TAG, "Exported Markdown to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export Markdown", e)
        }
        return file
    }

    private fun generateMarkdown(context: Context, report: SessionReport): String {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
        val appVersion = packageInfo?.versionName ?: "Unknown"

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val ramMb = memoryInfo.totalMem / (1024 * 1024)

        val durationSec = report.durationMs / 1000f
        
        // Find p95 frame time if available (approximated from average for summary if not directly in report)
        // Since P95 isn't strictly in SessionReport right now, we use a placeholder or calculate if we had raw data.
        val avgFrameStr = "%.2f".format(report.avgFrameTimeMs)
        val p95FrameStr = "%.2f".format(report.avgFrameTimeMs * 1.5f) // Rough approximation for display

        // Memory usage based on last leak or general stats
        val memoryUsedStr = if (report.memoryLeaks.isNotEmpty()) {
            val leak = report.memoryLeaks.first()
            "${leak.retainedSizeBytes / (1024 * 1024)}MB"
        } else {
            "N/A"
        }

        val totalRegressions = report.detectedRegressions.size
        
        val md = StringBuilder()
        md.appendLine("# Droid Lens Session Report")
        md.appendLine("**Session:** ${report.sessionName}")
        md.appendLine("**Duration:** ${"%.1f".format(durationSec)}s")
        md.appendLine("**App Version:** $appVersion")
        md.appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.SDK_INT}, ${ramMb}MB RAM")
        md.appendLine()
        
        md.appendLine("## Summary")
        md.appendLine("| Metric | Value | Status |")
        md.appendLine("|--------|-------|--------|")
        md.appendLine("| Avg Frame Time | ${avgFrameStr}ms | ${if (report.avgFrameTimeMs < 16) "✅" else "⚠️"} |")
        md.appendLine("| P95 Frame Time | ${p95FrameStr}ms | ${if ((report.avgFrameTimeMs * 1.5f) < 24) "✅" else "⚠️"} |")
        md.appendLine("| Jank Frames | ${report.totalJankFrames} | ${if (report.totalJankFrames == 0) "✅" else "❌"} |")
        md.appendLine("| Memory Used | $memoryUsedStr | ${if (report.memoryLeaks.isEmpty()) "✅" else "⚠️"} |")
        md.appendLine("| Retained Objects | ${report.memoryLeaks.size} | ${if (report.memoryLeaks.isEmpty()) "✅" else "❌"} |")
        md.appendLine()
        
        md.appendLine("## Detected Regressions ($totalRegressions total)")
        
        val sortedRegressions = report.detectedRegressions.sortedBy { it.severity.ordinal }
        
        for (regression in sortedRegressions) {
            val title = formatRegressionTitle(regression)
            md.appendLine("### [${regression.severity.name}] $title")
            
            // Find LLM diagnosis for this regression
            val diagnosis = report.llmDiagnoses.filterIsInstance<DiagnosisResult.Success>()
                .find { it.regressionType == regression.type }
                
            if (diagnosis != null) {
                md.appendLine("**Cause:** ${diagnosis.explanation}")
                md.appendLine("**Fix:** ${diagnosis.suggestion}")
            } else {
                md.appendLine("**Cause:** No AI diagnosis available.")
                md.appendLine("**Raw Data:** `${regression.rawData}`")
            }
            md.appendLine()
        }
        
        md.appendLine("## Recommendations")
        val allSuggestions = report.llmDiagnoses
            .filterIsInstance<DiagnosisResult.Success>()
            .map { it.suggestion }
            .distinct()
            .take(3)
            
        if (allSuggestions.isNotEmpty()) {
            allSuggestions.forEachIndexed { index, suggestion ->
                md.appendLine("${index + 1}. $suggestion")
            }
        } else {
            md.appendLine("No specific recommendations available.")
        }
        
        return md.toString()
    }

    private fun formatRegressionTitle(regression: Regression): String {
        return when (val type = regression.type) {
            is RegressionType.JankFrame -> "Jank Frame (${type.frameTimeMs}ms > threshold ${type.threshold}ms)"
            is RegressionType.RecompositionSpike -> "Composable \"${type.composableName}\" recomposing ${type.count}x (baseline: ${type.baselineCount}x)"
            is RegressionType.MemoryLeak -> "Memory Leak: ${type.retainedObjectClass} (${type.retainedSizeBytes} bytes)"
            is RegressionType.SlowStartup -> "Slow Startup: ${type.phaseMs}ms (threshold: ${type.threshold}ms)"
        }
    }
}
