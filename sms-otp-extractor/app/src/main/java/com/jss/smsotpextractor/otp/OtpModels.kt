package com.jss.smsotpextractor.otp

data class OtpCandidate(
    val index: Int,
    val value: String,
    val context: String,
    val score: Int = 0,
)

data class PrefilterResult(
    val possibleOtp: Boolean,
    val reason: String,
)

data class ScoredCandidates(
    val candidates: List<OtpCandidate>,
    val topCandidate: OtpCandidate?,
    val topScore: Int,
    val margin: Int,
)

data class AiOtpResult(
    val is2fa: Boolean,
    val candidateIndex: Int?,
    val confidence: Double,
    val rawOutput: String = "",
    val firstTokenMs: Double? = null,
    val promptToLastTokenMs: Double? = null,
)

sealed class OtpDecision {
    data class OtpDetected(
        val code: String,
        val source: String,
        val selectedCandidate: OtpCandidate,
        val candidates: List<OtpCandidate>,
        val timings: OtpTimings = OtpTimings(),
        val aiRawOutput: String? = null,
    ) : OtpDecision()

    data class NoOtp(
        val reason: String,
        val candidates: List<OtpCandidate> = emptyList(),
        val timings: OtpTimings = OtpTimings(),
        val aiRawOutput: String? = null,
    ) : OtpDecision()
}

data class OtpTimings(
    val candidateExtractionMs: Double = 0.0,
    val prefilterMs: Double = 0.0,
    val heuristicMs: Double = 0.0,
    val aiMs: Double = 0.0,
    val totalMs: Double = 0.0,
    val aiFirstTokenMs: Double? = null,
    val aiPromptToLastTokenMs: Double? = null,
)

interface AiOtpSelector {
    suspend fun select(sms: String, candidates: List<OtpCandidate>): AiOtpResult
}
