package com.jss.smsotpextractor

import android.content.Context
import com.jss.smsotpextractor.otp.OtpCandidate
import com.jss.smsotpextractor.otp.OtpDecision
import com.jss.smsotpextractor.otp.OtpTimings

object ResultStore {
    private const val PREFS = "otp_result"
    private const val KEY_TEXT = "text"
    private const val KEY_CODE = "code"

    fun save(context: Context, sms: String, decision: OtpDecision) {
        val formatted = formatDecision(sms, decision)
        val code = (decision as? OtpDecision.OtpDetected)?.code.orEmpty()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEXT, formatted)
            .putString(KEY_CODE, code)
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
}
