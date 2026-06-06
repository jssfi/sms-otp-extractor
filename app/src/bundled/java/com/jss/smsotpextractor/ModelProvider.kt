package com.jss.smsotpextractor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.jss.smsotpextractor.otp.AiOtpResult
import com.jss.smsotpextractor.otp.AiOtpJsonParser
import com.jss.smsotpextractor.otp.CandidateExtractor
import com.jss.smsotpextractor.otp.HeuristicScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object ModelProvider {
    private const val MODEL_ASSET = "models/SmolLM2_135M_Instruct.litertlm"
    const val MODEL_FILE_NAME = "SmolLM2_135M_Instruct.litertlm"
    private const val MIN_MODEL_BYTES = 1024L * 1024L

    suspend fun activeModelFile(context: Context): File? = withContext(Dispatchers.IO) {
        if (BuildConfig.BUNDLED_MODEL) {
            return@withContext ensureBundledModelFile(context)
        }
        ImportedModelStore.modelFile(context).takeIf { it.exists() && it.length() > MIN_MODEL_BYTES }
    }

    private fun ensureBundledModelFile(context: Context): File {
        val modelFile = File(context.filesDir, MODEL_FILE_NAME)
        if (modelFile.exists() && modelFile.length() > MIN_MODEL_BYTES) {
            return modelFile
        }

        context.assets.open(MODEL_ASSET).use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return modelFile
    }
}

data class ImportedModelMetadata(
    val displayName: String,
    val fileSizeBytes: Long,
    val importedAtMs: Long,
    val compatible: Boolean,
    val averageLatencyMs: Double,
    val warning: String?,
) {
    val hasWarning: Boolean
        get() = warning != null
}

sealed class ModelImportResult {
    data class Imported(val metadata: ImportedModelMetadata) : ModelImportResult()
    data class Failed(val message: String) : ModelImportResult()
}

object ImportedModelStore {
    private const val PREFS = "imported_model"
    private const val KEY_METADATA = "metadata"
    private const val IMPORTED_FILE_NAME = "imported_model.litertlm"

    fun modelFile(context: Context): File = File(context.filesDir, IMPORTED_FILE_NAME)

    fun metadata(context: Context): ImportedModelMetadata? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_METADATA, null)
            ?: return null
        return runCatching {
            val json = JSONObject(raw)
            ImportedModelMetadata(
                displayName = json.optString("displayName"),
                fileSizeBytes = json.optLong("fileSizeBytes"),
                importedAtMs = json.optLong("importedAtMs"),
                compatible = json.optBoolean("compatible"),
                averageLatencyMs = json.optDouble("averageLatencyMs"),
                warning = json.optString("warning").takeIf { it.isNotBlank() && it != "null" },
            )
        }.getOrNull()
    }

    fun save(context: Context, metadata: ImportedModelMetadata) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_METADATA, metadata.toJson().toString())
            .apply()
    }

    fun clear(context: Context) {
        modelFile(context).delete()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_METADATA)
            .apply()
    }

    private fun ImportedModelMetadata.toJson(): JSONObject {
        return JSONObject()
            .put("displayName", displayName)
            .put("fileSizeBytes", fileSizeBytes)
            .put("importedAtMs", importedAtMs)
            .put("compatible", compatible)
            .put("averageLatencyMs", averageLatencyMs)
            .put("warning", warning)
    }
}

object ModelImporter {
    suspend fun import(context: Context, uri: Uri): ModelImportResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val displayName = displayName(appContext, uri) ?: "Selected model"
        if (!displayName.endsWith(".litertlm", ignoreCase = true)) {
            return@withContext ModelImportResult.Failed("Choose a .litertlm model file.")
        }

        val destination = ImportedModelStore.modelFile(appContext)
        val temp = File(appContext.filesDir, "${destination.name}.tmp")
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext ModelImportResult.Failed("Could not open the selected file.")
        }.getOrElse { error ->
            temp.delete()
            return@withContext ModelImportResult.Failed("Import failed: ${error.message ?: "unknown file error"}")
        }

        if (temp.length() <= 0L) {
            temp.delete()
            return@withContext ModelImportResult.Failed("The selected model file is empty.")
        }

        if (destination.exists()) destination.delete()
        if (!temp.renameTo(destination)) {
            temp.delete()
            return@withContext ModelImportResult.Failed("Could not save the model in app storage.")
        }

        val benchmark = ModelBenchmark.run(appContext, destination)
        if (!benchmark.initialized) {
            destination.delete()
            return@withContext ModelImportResult.Failed("LiteRT could not initialize this model.")
        }

        val metadata = ImportedModelMetadata(
            displayName = displayName,
            fileSizeBytes = destination.length(),
            importedAtMs = System.currentTimeMillis(),
            compatible = true,
            averageLatencyMs = benchmark.averageLatencyMs,
            warning = benchmark.warning,
        )
        ImportedModelStore.save(appContext, metadata)
        ModelImportResult.Imported(metadata)
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}

data class ModelBenchmarkResult(
    val initialized: Boolean,
    val averageLatencyMs: Double = 0.0,
    val warning: String? = null,
)

object ModelBenchmark {
    private const val SLOW_AVERAGE_MS = 2_500.0

    suspend fun run(context: Context, modelFile: File): ModelBenchmarkResult = withContext(Dispatchers.IO) {
        val engine = runCatching {
            Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                ),
            ).also { it.initialize() }
        }.getOrElse {
            return@withContext ModelBenchmarkResult(initialized = false)
        }

        engine.use {
            val results = smokeCases().map { smokeCase ->
                runCatching {
                    val startedAt = System.nanoTime()
                    val result = LiteRtPromptRunner.run(engine, smokeCase.sms, smokeCase.candidates)
                    SmokeRun(
                        case = smokeCase,
                        result = result,
                        latencyMs = (System.nanoTime() - startedAt) / 1_000_000.0,
                        parseable = AiOtpJsonParser.parse(result.rawOutput) != null,
                    )
                }.getOrElse { error ->
                    SmokeRun(
                        case = smokeCase,
                        result = AiOtpResult(false, null, 0.0, error.message.orEmpty()),
                        latencyMs = SLOW_AVERAGE_MS,
                        parseable = false,
                    )
                }
            }

            val averageLatency = results.map { it.latencyMs }.average().takeIf { !it.isNaN() } ?: 0.0
            val parseableCount = results.count { it.parseable }
            val accuracyOk = results.count { it.case.accepts(it.result) } >= 2

            val warning = when {
                parseableCount < 2 -> "This model may not follow the required output format."
                averageLatency > SLOW_AVERAGE_MS -> "This model may be slow for SMS processing."
                !accuracyOk -> "This model may reduce detection accuracy."
                else -> null
            }
            ModelBenchmarkResult(
                initialized = true,
                averageLatencyMs = averageLatency,
                warning = warning,
            )
        }
    }

    fun classifyForTests(
        parseableCount: Int,
        correctCount: Int,
        averageLatencyMs: Double,
    ): String? {
        return when {
            parseableCount < 2 -> "This model may not follow the required output format."
            averageLatencyMs > SLOW_AVERAGE_MS -> "This model may be slow for SMS processing."
            correctCount < 2 -> "This model may reduce detection accuracy."
            else -> null
        }
    }

    private fun smokeCases(): List<SmokeCase> {
        return listOf(
            SmokeCase(
                sms = "Your login code is 482913. Ref 20260605.",
                expected2fa = true,
                expectedIndex = 0,
            ),
            SmokeCase(
                sms = "Your balance is 482913 EUR.",
                expected2fa = false,
                expectedIndex = null,
            ),
            SmokeCase(
                sms = "2FA values: 111111111 and 222222222",
                expected2fa = true,
                expectedIndex = null,
            ),
        ).map { it.withCandidates() }
    }

    private data class SmokeCase(
        val sms: String,
        val expected2fa: Boolean,
        val expectedIndex: Int?,
        val candidates: List<com.jss.smsotpextractor.otp.OtpCandidate> = emptyList(),
    ) {
        fun withCandidates(): SmokeCase {
            val scored = HeuristicScorer.score(sms, CandidateExtractor.extract(sms))
            return copy(candidates = scored.candidates)
        }

        fun accepts(result: AiOtpResult): Boolean {
            if (expected2fa != result.is2fa) return false
            if (!result.is2fa) return true
            val index = result.candidateIndex ?: return false
            if (index !in candidates.indices) return false
            return expectedIndex == null || index == expectedIndex
        }
    }

    private data class SmokeRun(
        val case: SmokeCase,
        val result: AiOtpResult,
        val latencyMs: Double,
        val parseable: Boolean,
    )
}
