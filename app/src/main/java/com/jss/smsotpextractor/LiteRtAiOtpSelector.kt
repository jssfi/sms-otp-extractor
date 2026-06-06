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
        return runCatching {
            EngineHolder.select(appContext, sms, candidates)
        }.getOrElse { error ->
            AiOtpResult(
                is2fa = false,
                candidateIndex = null,
                confidence = 0.0,
                rawOutput = "AI unavailable: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    private object EngineHolder {
        private val mutex = Mutex()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val releaseScope = CoroutineScope(Dispatchers.IO)
        private var engine: Engine? = null
        private var engineModelPath: String? = null
        private var engineModelStamp: String? = null
        private var releaseRunnable: Runnable? = null

        suspend fun select(context: Context, sms: String, candidates: List<OtpCandidate>): AiOtpResult {
            return mutex.withLock {
                val modelFile = ModelProvider.activeModelFile(context)
                    ?: return@withLock AiOtpResult(
                        is2fa = false,
                        candidateIndex = null,
                        confidence = 0.0,
                        rawOutput = "AI unavailable: no LiteRT model imported",
                    )
                val activeEngine = ensureEngine(context, modelFile)
                releaseRunnable?.let(mainHandler::removeCallbacks)

                try {
                    LiteRtPromptRunner.run(activeEngine, sms, candidates)
                } finally {
                    scheduleRelease()
                }
            }
        }

        private suspend fun ensureEngine(context: Context, modelFile: File): Engine = withContext(Dispatchers.IO) {
            val modelPath = modelFile.absolutePath
            val modelStamp = "${modelFile.length()}:${modelFile.lastModified()}"
            if (engineModelPath != modelPath || engineModelStamp != modelStamp) {
                engine?.close()
                engine = null
                engineModelPath = null
                engineModelStamp = null
            }
            engine?.let { return@withContext it }

            val created = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                ),
            )
            created.initialize()
            engine = created
            engineModelPath = modelPath
            engineModelStamp = modelStamp
            created
        }

        private fun scheduleRelease() {
            val runnable = Runnable {
                releaseScope.launch {
                    mutex.withLock {
                        engine?.close()
                        engine = null
                        engineModelPath = null
                        engineModelStamp = null
                    }
                }
            }
            releaseRunnable = runnable
            mainHandler.postDelayed(runnable, WARM_TIMEOUT_MS)
        }
    }

    private companion object {
        const val WARM_TIMEOUT_MS = 60_000L
    }
}

object LiteRtPromptRunner {
    suspend fun run(
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

    private fun elapsedMs(startNanos: Long, endNanos: Long): Double {
        return (endNanos - startNanos) / 1_000_000.0
    }
}
