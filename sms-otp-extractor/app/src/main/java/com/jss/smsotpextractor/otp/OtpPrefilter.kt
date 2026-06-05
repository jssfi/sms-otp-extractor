package com.jss.smsotpextractor.otp

object OtpPrefilter {
    private val positiveKeywords = listOf(
        "otp",
        "code",
        "verification",
        "verify",
        "login",
        "sign in",
        "signin",
        "2fa",
        "two-factor",
        "authentication",
        "passcode",
        "one-time",
        "security code",
    )

    private val negativeKeywords = listOf(
        "balance",
        "statement",
        "receipt",
        "invoice",
        "marketing",
        "offer",
        "sale",
        "appointment",
    )

    fun classify(sms: String, candidates: List<OtpCandidate>): PrefilterResult {
        if (candidates.isEmpty()) {
            return PrefilterResult(possibleOtp = false, reason = "no_candidates")
        }

        val normalized = sms.lowercase()
        val hasNegative = negativeKeywords.any { it in normalized }
        if (hasNegative) {
            return PrefilterResult(possibleOtp = false, reason = "negative_keyword")
        }

        val hasPositive = positiveKeywords.any { it in normalized }
        if (hasPositive) {
            return PrefilterResult(possibleOtp = true, reason = "otp_keyword")
        }

        return PrefilterResult(possibleOtp = true, reason = "candidate_only")
    }
}
