package com.jss.smsotpextractor

import android.content.Context
import android.content.SharedPreferences
import com.jss.smsotpextractor.otp.OtpCandidate
import com.jss.smsotpextractor.otp.OtpDecision
import com.jss.smsotpextractor.otp.OtpTimings
import org.json.JSONArray
import org.json.JSONObject

object ResultStore {
    private const val PREFS = "otp_result"
    private const val KEY_TEXT = "text"
    private const val KEY_CODE = "code"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 20

    data class HistoryItem(
        val receivedAtMs: Long,
        val sms: String,
        val title: String,
        val detail: String,
        val code: String?,
        val source: String?,
        val candidates: List<String>,
    )

    fun save(context: Context, sms: String, decision: OtpDecision) {
        val formatted = formatDecision(sms, decision)
        val code = (decision as? OtpDecision.OtpDetected)?.code.orEmpty()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val history = buildList {
            add(historyItem(sms, decision))
            addAll(history(context))
        }.take(MAX_HISTORY)

        prefs.edit()
            .putString(KEY_TEXT, formatted)
            .putString(KEY_CODE, code)
            .putString(KEY_HISTORY, history.toJson().toString())
            .apply()
    }

    fun latestText(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TEXT, "No SMS processed yet.")
            ?: "No SMS processed yet."
    }

    fun latestCode(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun history(context: Context): List<HistoryItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toHistoryItem())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_TEXT)
            .remove(KEY_CODE)
            .remove(KEY_HISTORY)
            .apply()
    }

    fun registerHistoryListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterHistoryListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isHistoryKey(key: String?): Boolean {
        return key == KEY_HISTORY || key == KEY_CODE || key == KEY_TEXT
    }

    fun formatDecision(sms: String, decision: OtpDecision): String {
        return buildString {
            appendLine("SMS:")
            appendLine(sms)
            appendLine()
            when (decision) {
                is OtpDecision.OtpDetected -> {
                    appendLine("Decision: OTP detected")
                    appendLine("Code: ${decision.code}")
                    appendLine("Source: ${decision.source}")
                    appendLine("Clipboard: copied automatically")
                    appendLine("Selected candidate: ${decision.selectedCandidate.value}")
                    appendCandidates(decision.candidates)
                    decision.aiRawOutput?.let {
                        appendLine()
                        appendLine("AI raw output:")
                        appendLine(it)
                    }
                    appendTimings(decision.timings)
                }

                is OtpDecision.NoOtp -> {
                    appendLine("Decision: no OTP")
                    appendLine("Reason: ${decision.reason}")
                    appendCandidates(decision.candidates)
                    decision.aiRawOutput?.let {
                        appendLine()
                        appendLine("AI raw output:")
                        appendLine(it)
                    }
                    appendTimings(decision.timings)
                }
            }
        }
    }

    private fun StringBuilder.appendCandidates(candidates: List<OtpCandidate>) {
        if (candidates.isEmpty()) return
        appendLine()
        appendLine("Candidates:")
        candidates.forEach { candidate ->
            appendLine("${candidate.index}: ${candidate.value} score=${candidate.score} context=\"${candidate.context}\"")
        }
    }

    private fun StringBuilder.appendTimings(timings: OtpTimings) {
        appendLine()
        appendLine("Timings:")
        appendLine("Candidate extraction: ${timings.candidateExtractionMs.formatMs()}")
        appendLine("Prefilter: ${timings.prefilterMs.formatMs()}")
        appendLine("Heuristic: ${timings.heuristicMs.formatMs()}")
        appendLine("AI: ${timings.aiMs.formatMs()}")
        timings.aiFirstTokenMs?.let { appendLine("AI prompt to first token: ${it.formatMs()}") }
        timings.aiPromptToLastTokenMs?.let { appendLine("AI prompt to last token: ${it.formatMs()}") }
        appendLine("Total: ${timings.totalMs.formatMs()}")
    }

    private fun Double.formatMs(): String = "%.1f ms".format(this)

    private fun historyItem(sms: String, decision: OtpDecision): HistoryItem {
        return when (decision) {
            is OtpDecision.OtpDetected -> HistoryItem(
                receivedAtMs = System.currentTimeMillis(),
                sms = sms,
                title = "Code ${decision.code}",
                detail = "Recognized by ${decision.sourceLabel()} in ${decision.timings.totalMs.formatMs()}.",
                code = decision.code,
                source = decision.source,
                candidates = decision.candidates.map { "${it.value} (${it.score})" },
            )

            is OtpDecision.NoOtp -> HistoryItem(
                receivedAtMs = System.currentTimeMillis(),
                sms = sms,
                title = "Not recognized",
                detail = decision.reasonLabel(),
                code = null,
                source = null,
                candidates = decision.candidates.map { "${it.value} (${it.score})" },
            )
        }
    }

    private fun OtpDecision.OtpDetected.sourceLabel(): String {
        return when (source) {
            "heuristic" -> "local rules"
            "heuristic_fallback" -> "fallback rules"
            "ai" -> "AI"
            else -> source
        }
    }

    private fun OtpDecision.NoOtp.reasonLabel(): String {
        return when (reason) {
            "no_candidates" -> "Blocked because no 4-10 digit or 6-12 character mixed code was found."
            "negative_keyword" -> "Blocked because the message looked like a receipt, offer, invoice, or similar non-login SMS."
            "low_confidence" -> "Blocked because the candidates were too weak after scoring and AI review."
            else -> "Blocked by prefilter: $reason."
        }
    }

    private fun List<HistoryItem>.toJson(): JSONArray {
        val array = JSONArray()
        forEach { item ->
            array.put(
                JSONObject()
                    .put("receivedAtMs", item.receivedAtMs)
                    .put("sms", item.sms)
                    .put("title", item.title)
                    .put("detail", item.detail)
                    .put("code", item.code)
                    .put("source", item.source)
                    .put("candidates", JSONArray(item.candidates)),
            )
        }
        return array
    }

    private fun JSONObject.toHistoryItem(): HistoryItem {
        val rawCandidates = optJSONArray("candidates") ?: JSONArray()
        return HistoryItem(
            receivedAtMs = optLong("receivedAtMs"),
            sms = optString("sms"),
            title = optString("title"),
            detail = optString("detail"),
            code = optString("code").takeIf { it.isNotBlank() && it != "null" },
            source = optString("source").takeIf { it.isNotBlank() && it != "null" },
            candidates = buildList {
                for (index in 0 until rawCandidates.length()) {
                    add(rawCandidates.optString(index))
                }
            },
        )
    }
}
