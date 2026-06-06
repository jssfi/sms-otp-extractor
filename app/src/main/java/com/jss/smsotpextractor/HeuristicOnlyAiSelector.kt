package com.jss.smsotpextractor

import com.jss.smsotpextractor.otp.AiOtpResult
import com.jss.smsotpextractor.otp.AiOtpSelector
import com.jss.smsotpextractor.otp.OtpCandidate

object HeuristicOnlyAiSelector : AiOtpSelector {
    override suspend fun select(sms: String, candidates: List<OtpCandidate>): AiOtpResult {
        return AiOtpResult(
            is2fa = false,
            candidateIndex = null,
            confidence = 0.0,
            rawOutput = "AI disabled: heuristic-only build",
        )
    }
}
