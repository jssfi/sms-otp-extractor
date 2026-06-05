package com.jss.smsotpextractor.otp

object HeuristicScorer {
    private val strongPositive = listOf("otp", "code", "verification", "verify", "login", "passcode")
    private val weakPositive = listOf("security", "authenticate", "authentication", "2fa", "sign in", "signin")
    private val referenceWords = listOf("ref", "reference", "id", "ticket", "case", "receipt")

    fun score(sms: String, candidates: List<OtpCandidate>): ScoredCandidates {
        val scored = candidates.map { candidate ->
            candidate.copy(score = scoreCandidate(sms, candidate).coerceIn(0, 100))
        }.sortedByDescending { it.score }

        val top = scored.firstOrNull()
        val second = scored.drop(1).firstOrNull()
        return ScoredCandidates(
            candidates = scored.sortedBy { it.index },
            topCandidate = top,
            topScore = top?.score ?: 0,
            margin = (top?.score ?: 0) - (second?.score ?: 0),
        )
    }

    private fun scoreCandidate(sms: String, candidate: OtpCandidate): Int {
        val value = candidate.value
        val context = candidate.context.lowercase()
        val normalizedSms = sms.lowercase()
        var score = 10

        if (value.length in 4..8) score += 20
        if (value.all(Char::isDigit) && value.length in 5..6) score += 20
        if (value.any(Char::isLetter) && value.any(Char::isDigit)) score += 8

        if (strongPositive.any { it in context }) score += 35
        if (weakPositive.any { it in context }) score += 15
        if (strongPositive.any { it in normalizedSms }) score += 8

        if (referenceWords.any { word -> Regex("""\b$word\W+${Regex.escape(value.lowercase())}\b""").containsMatchIn(context) }) score -= 30
        if (looksLikeDate(value)) score -= 45
        if (looksLikePhoneOrLongReference(value)) score -= 35
        if (looksLikeMoneyContext(context)) score -= 20

        return score
    }

    private fun looksLikeDate(value: String): Boolean {
        if (!value.all(Char::isDigit) || value.length != 8) return false
        val year = value.take(4).toIntOrNull() ?: return false
        val month = value.substring(4, 6).toIntOrNull() ?: return false
        val day = value.takeLast(2).toIntOrNull() ?: return false
        return year in 2000..2099 && month in 1..12 && day in 1..31
    }

    private fun looksLikePhoneOrLongReference(value: String): Boolean {
        return value.all(Char::isDigit) && value.length >= 9
    }

    private fun looksLikeMoneyContext(context: String): Boolean {
        return listOf("eur", "usd", "$", "€", "amount", "paid", "payment").any { it in context }
    }
}
