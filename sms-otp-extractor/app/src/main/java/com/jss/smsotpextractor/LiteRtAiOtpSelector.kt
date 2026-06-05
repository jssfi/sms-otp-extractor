package com.jss.smsotpextractor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.jss.smsotpextractor.otp.AiOtpJsonParser
import com.jss.smsotpextractor.otp.AiOtpResult
import com.jss.smsotpextractor.otp.AiOtpSelector
import com.jss.smsotpextractor.otp.OtpCandidate
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class LiteRtAiOtpSelector(
    context: Context,
) : AiOtpSelector {
    private val appContext = context.applicationContext

    override suspend fun select(sms: String, candidates: List<OtpCandidate>): AiOtpResult {
        return EngineHolder.select(appContext, sms, candidates)
    }

    private object EngineHolder {
        private val mutex = Mutex()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val releaseScope = CoroutineScope(Dispatchers.IO)
        private var engine: Engine? = null
        private var releaseRunnable: Runnable? = null

        suspend fun select(context: Context, sms: String, candidates: List<OtpCandidate>): AiOtpResult {
            return mutex.withLock {
                val modelFile = ensureModelFile(context)
                val activeEngine = ensureEngine(context, modelFile)
                releaseRunnable?.let(mainHandler::removeCallbacks)

                try {
                    runPrompt(activeEngine, sms, candidates)
                } finally {
                    scheduleRelease()
                }
            }
        }

        private suspend fun ensureModelFile(context: Context): File = withContext(Dispatchers.IO) {
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            if (modelFile.exists() && modelFile.length() > MIN_MODEL_BYTES) {
                return@withContext modelFile
            }

            context.assets.open(MODEL_ASSET).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            modelFile
        }

        private suspend fun ensureEngine(context: Context, modelFile: File): Engine = withContext(Dispatchers.IO) {
            engine?.let { return@withContext it }

            val created = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                ),
            )
            created.initialize()
            engine = created
            created
        }

        private suspend fun runPrompt(
            engine: Engine,
            sms: String,
            candidates: List<OtpCandidate>,
        ): AiOtpResult = withContext(Dispatchers.IO) {
            val prompt = buildPrompt(sms, candidates)
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(
                    "You are an OTP candidate classifier. Return one compact JSON object only. Never invent codes.",
                ),
                samplerConfig = SamplerConfig(
                    topK = 1,
                    topP = 0.0,
                    temperature = 0.0,
                ),
            )

            engine.createConversation(conversationConfig).use { conversation ->
                val promptStartedAt = SystemClock.elapsedRealtimeNanos()
                var firstTokenAt: Long? = null
                val response = StringBuilder()

                conversation.sendMessageAsync(prompt)
                    .flowOn(Dispatchers.IO)
                    .collect { chunk ->
                        val tokenAt = SystemClock.elapsedRealtimeNanos()
                        if (firstTokenAt == null) firstTokenAt = tokenAt
                        response.append(chunk.toString())
                    }

                val lastTokenAt = SystemClock.elapsedRealtimeNanos()
                val raw = response.toString()
                val parsed = AiOtpJsonParser.parse(raw)
                parsed?.copy(
                    rawOutput = raw,
                    firstTokenMs = firstTokenAt?.let { elapsedMs(promptStartedAt, it) },
                    promptToLastTokenMs = elapsedMs(promptStartedAt, lastTokenAt),
                ) ?: AiOtpResult(
                    is2fa = false,
                    candidateIndex = null,
                    confidence = 0.0,
                    rawOutput = raw,
                    firstTokenMs = firstTokenAt?.let { elapsedMs(promptStartedAt, it) },
                    promptToLastTokenMs = elapsedMs(promptStartedAt, lastTokenAt),
                )
            }
        }

        private fun buildPrompt(sms: String, candidates: List<OtpCandidate>): String {
            val candidatesText = candidates.joinToString(separator = "\n") { candidate ->
                """${candidate.index}: "${candidate.value}" context="${candidate.context}" score=${candidate.score}"""
            }
            return """
                Return JSON only.
                You select OTP codes from SMS messages.
                Choose only from the provided candidates.
                Never invent a code.
                If this is not a 2FA/login/verification SMS, return:
                {"is_2fa":false,"candidate_index":null,"confidence":0}

                SMS:
                "$sms"

                Candidates:
                $candidatesText

                Return:
                {"is_2fa":true,"candidate_index":0,"confidence":0.95}
            """.trimIndent()
        }

        private fun scheduleRelease() {
            val runnable = Runnable {
                releaseScope.launch {
                    mutex.withLock {
                        engine?.close()
                        engine = null
                    }
                }
            }
            releaseRunnable = runnable
            mainHandler.postDelayed(runnable, WARM_TIMEOUT_MS)
        }

        private fun elapsedMs(startNanos: Long, endNanos: Long): Double {
            return (endNanos - startNanos) / 1_000_000.0
        }
    }

    private companion object {
        const val MODEL_ASSET = "models/SmolLM2_135M_Instruct.litertlm"
        const val MODEL_FILE_NAME = "SmolLM2_135M_Instruct.litertlm"
        const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
        const val WARM_TIMEOUT_MS = 60_000L
    }
}
