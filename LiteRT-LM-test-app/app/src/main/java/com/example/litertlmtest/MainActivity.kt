package com.example.litertlmtest

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var testButton: Button
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
            text = "Ready to test the bundled local model."
        }

        testButton = Button(this).apply {
            text = "Test Bundled Mini Model"
            setOnClickListener { testBundledModel() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 40, 32, 32)
            addView(
                testButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                output,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = 24 },
            )
        }

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun testBundledModel() {
        testButton.isEnabled = false
        output.text = "Testing..."

        scope.launch {
            val logLines = mutableListOf<String>()
            fun append(line: String) {
                logLines += line
                output.text = logLines.joinToString(separator = "\n")
                Log.d(TAG, line)
            }

            try {
                val smsArrivedAt = SystemClock.elapsedRealtimeNanos()
                append(deviceInfo())
                append("Runtime: LiteRT-LM 0.13.1, Backend.CPU")
                append("Simulation: SMS arrived -> model cached -> model loaded -> prompt -> last token")
                append("Bundled model: $BUNDLED_MODEL_ASSET")
                append("Copying bundled model into app cache...")

                val copyStartedAt = SystemClock.elapsedRealtimeNanos()
                val modelFile = withContext(Dispatchers.IO) { copyBundledModelToCache() }
                val copyEndedAt = SystemClock.elapsedRealtimeNanos()
                append("Cached model path: ${modelFile.absolutePath}")
                append("Cached model size: ${modelFile.length()} bytes")
                append("Model cache/copy time: ${formatDuration(copyStartedAt, copyEndedAt)}")
                append("Initializing engine. This can take a while.")

                runPrompt(modelFile, smsArrivedAt, copyEndedAt, ::append)
            } catch (throwable: Throwable) {
                append("Exception class: ${throwable::class.java.name}")
                append("Exception message: ${throwable.message ?: throwable.toString()}")
                Log.e(TAG, "LiteRT-LM test failed", throwable)
            } finally {
                testButton.isEnabled = true
            }
        }
    }

    private suspend fun runPrompt(
        modelFile: File,
        smsArrivedAt: Long,
        modelCachedAt: Long,
        append: (String) -> Unit,
    ) {
        val prompt = """
            Extract the login code from this SMS.
            SMS: "Your login code is 123456. Ref 20260605"
            Valid candidates: 123456, 20260605
            The login code is the value after "login code is".
            Return only this exact JSON object:
            {"ok":true,"code":"123456"}
        """.trimIndent()

        withContext(Dispatchers.IO) {
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDir.absolutePath,
            )

            val engineOpenedAt = SystemClock.elapsedRealtimeNanos()
            Engine(engineConfig).use { engine ->
                val engineInitStartedAt = SystemClock.elapsedRealtimeNanos()
                engine.initialize()
                val engineInitializedAt = SystemClock.elapsedRealtimeNanos()
                withContext(Dispatchers.Main) {
                    append("Engine initialized.")
                    append("Prompt: $prompt")
                }

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(
                        "You are a JSON extraction engine. Return one compact JSON object only. No markdown. No explanations.",
                    ),
                    samplerConfig = SamplerConfig(
                        topK = 1,
                        topP = 0.0,
                        temperature = 0.0,
                    ),
                )

                val conversationCreateStartedAt = SystemClock.elapsedRealtimeNanos()
                engine.createConversation(conversationConfig).use { conversation ->
                    val conversationCreatedAt = SystemClock.elapsedRealtimeNanos()
                    val promptStartedAt = SystemClock.elapsedRealtimeNanos()
                    var firstTokenAt: Long? = null
                    var tokenCount = 0
                    val response = StringBuilder()

                    conversation.sendMessageAsync(prompt)
                        .flowOn(Dispatchers.IO)
                        .collect { chunk ->
                            val tokenAt = SystemClock.elapsedRealtimeNanos()
                            if (firstTokenAt == null) {
                                firstTokenAt = tokenAt
                            }
                            tokenCount += 1
                            response.append(chunk.toString())
                        }

                    val lastTokenAt = SystemClock.elapsedRealtimeNanos()
                    val firstToken = firstTokenAt ?: lastTokenAt
                    withContext(Dispatchers.Main) {
                        append("Success output:")
                        append(response.toString())
                        append("")
                        append("Timings:")
                        append("Model cache/copy: ${formatDuration(smsArrivedAt, modelCachedAt)}")
                        append("Engine object creation: ${formatDuration(engineOpenedAt, engineInitStartedAt)}")
                        append("Engine initialize: ${formatDuration(engineInitStartedAt, engineInitializedAt)}")
                        append("Conversation create: ${formatDuration(conversationCreateStartedAt, conversationCreatedAt)}")
                        append("Prompt to first token: ${formatDuration(promptStartedAt, firstToken)}")
                        append("First token to last token: ${formatDuration(firstToken, lastTokenAt)}")
                        append("Prompt to last token: ${formatDuration(promptStartedAt, lastTokenAt)}")
                        append("Model cached to last token: ${formatDuration(modelCachedAt, lastTokenAt)}")
                        append("SMS arrived to last token: ${formatDuration(smsArrivedAt, lastTokenAt)}")
                        append("Chunks received: $tokenCount")
                    }
                }
            }
        }
    }

    private fun copyBundledModelToCache(): File {
        val file = File(cacheDir, "selected-model.litertlm")
        assets.open(BUNDLED_MODEL_ASSET).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private fun deviceInfo(): String {
        return "Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; SDK ${Build.VERSION.SDK_INT}"
    }

    private fun formatDuration(startNanos: Long, endNanos: Long): String {
        val millis = (endNanos - startNanos) / 1_000_000.0
        return "%.1f ms (%.3f s)".format(millis, millis / 1_000.0)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LiteRtLmTest"
        const val BUNDLED_MODEL_ASSET = "models/SmolLM2_135M_Instruct.litertlm"
    }
}
