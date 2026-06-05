package com.jss.smsotpextractor.otp

object AiOtpJsonParser {
    private val is2faRegex = Regex(""""is_2fa"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
    private val indexRegex = Regex(""""candidate_index"\s*:\s*(null|-?\d+)""", RegexOption.IGNORE_CASE)
    private val confidenceRegex = Regex(""""confidence"\s*:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): AiOtpResult? {
        val json = raw.substringAfter("{", missingDelimiterValue = "").substringBeforeLast("}", missingDelimiterValue = "")
        if (json.isBlank()) return null
        val body = "{$json}"
        val is2fa = is2faRegex.find(body)?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: return null
        val candidateIndex = indexRegex.find(body)?.groupValues?.get(1)?.let { value ->
            if (value.equals("null", ignoreCase = true)) null else value.toIntOrNull()
        }
        val confidence = confidenceRegex.find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        return AiOtpResult(
            is2fa = is2fa,
            candidateIndex = candidateIndex,
            confidence = confidence,
            rawOutput = raw,
        )
    }
}
