package com.jss.smsotpextractor.otp

import java.text.Normalizer

object HeuristicScorer {
    private val strongPositive = listOf(
        "otp",
        "code",
        "pin",
        "verification",
        "verify",
        "verifica",
        "verificar",
        "verificacion",
        "verifizierung",
        "verifiering",
        "verifieringskod",
        "confirma",
        "confirmar",
        "confirme",
        "login",
        "inloggning",
        "kirjautuminen",
        "passcode",
        "koodi",
        "koodilla",
        "vahvistus",
        "vahvista",
        "tunnusluku",
        "codigo",
        "codice",
        "kod",
        "kennwort",
        "tan",
    )

    private val specificOtpPositive = strongPositive - listOf("code", "pin", "kod", "codigo", "codice")

    private val weakPositive = listOf(
        "security",
        "authenticate",
        "authentication",
        "2fa",
        "two-factor",
        "sign in",
        "signin",
        "sign-in",
        "expires",
        "expire",
        "minutes",
        "minute",
        "min",
        "share",
        "do not share",
        "don't share",
        "dont share",
        "valid",
        "use",
    )

    private val referenceWords = listOf(
        "ref",
        "reference",
        "id",
        "ticket",
        "case",
        "receipt",
        "invoice",
        "order",
        "tracking",
        "shipment",
        "delivery",
        "customer",
        "account",
        "iban",
        "card",
    )

    private val hardNegativeWords = listOf(
        "balance",
        "statement",
        "receipt",
        "invoice",
        "marketing",
        "offer",
        "sale",
        "appointment",
        "meeting",
        "room",
        "tracking",
        "shipment",
        "delivery",
        "paid",
        "payment",
        "charged",
        "coupon",
    )

    private const val BASE_SCORE = 10
    private const val OTP_LENGTH_SCORE = 20
    private const val COMMON_NUMERIC_LENGTH_SCORE = 20
    private const val ALPHANUMERIC_SCORE = 8
    private const val LOCAL_STRONG_KEYWORD_SCORE = 35
    private const val LOCAL_WEAK_KEYWORD_SCORE = 15
    private const val MESSAGE_STRONG_KEYWORD_SCORE = 8
    private const val PUNCTUATION_PATTERN_SCORE = 18
    private const val OWNERSHIP_PATTERN_SCORE = 16
    private const val EXPIRY_OR_SHARING_SCORE = 12
    private const val SHORT_MESSAGE_SCORE = 8
    private const val BEFORE_URL_SCORE = 8
    private const val REPEATED_CODE_SCORE = 10
    private const val TRANSACTION_OTP_PATTERN_SCORE = 45
    private const val DIRECT_REFERENCE_PENALTY = 30
    private const val REFERENCE_CONTEXT_PENALTY = 12
    private const val HARD_NEGATIVE_CONTEXT_PENALTY = 22
    private const val DATE_PENALTY = 45
    private const val TIME_PENALTY = 30
    private const val LONG_REFERENCE_PENALTY = 35
    private const val URL_QUERY_PENALTY = 35
    private const val PERCENT_PENALTY = 25
    private const val MONEY_CONTEXT_PENALTY = 20
    private const val DECIMAL_MONEY_PENALTY = 50

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
        val context = normalizeText(candidate.context)
        val normalizedSms = normalizeText(sms)
        var score = BASE_SCORE

        if (value.length in 4..8) score += OTP_LENGTH_SCORE
        if (value.all(Char::isDigit) && value.length in 5..6) score += COMMON_NUMERIC_LENGTH_SCORE
        if (value.any(Char::isLetter) && value.any(Char::isDigit)) score += ALPHANUMERIC_SCORE

        if (containsAnyKeyword(context, strongPositive)) score += LOCAL_STRONG_KEYWORD_SCORE
        if (containsAnyKeyword(context, weakPositive)) score += LOCAL_WEAK_KEYWORD_SCORE
        if (containsAnyKeyword(normalizedSms, strongPositive)) score += MESSAGE_STRONG_KEYWORD_SCORE
        if (hasOtpPunctuationPattern(value, context)) score += PUNCTUATION_PATTERN_SCORE
        if (hasOwnershipPattern(value, context)) score += OWNERSHIP_PATTERN_SCORE
        if (hasExpiryOrSharingSignal(context)) score += EXPIRY_OR_SHARING_SCORE
        if (isShortOtpLikeMessage(normalizedSms)) score += SHORT_MESSAGE_SCORE
        if (appearsBeforeUrl(value, sms)) score += BEFORE_URL_SCORE
        if (isRepeated(value, sms)) score += REPEATED_CODE_SCORE
        if (hasTransactionOtpPattern(value, normalizedSms)) score += TRANSACTION_OTP_PATTERN_SCORE

        if (referenceWords.any { word -> Regex("""(?<![a-z0-9])$word\W+${Regex.escape(value.lowercase())}\b""").containsMatchIn(context) }) score -= DIRECT_REFERENCE_PENALTY
        if (containsAnyKeyword(context, referenceWords) && !containsAnyKeyword(context, strongPositive)) score -= REFERENCE_CONTEXT_PENALTY
        if (containsAnyKeyword(context, hardNegativeWords) && !containsAnyKeyword(context, specificOtpPositive)) score -= HARD_NEGATIVE_CONTEXT_PENALTY
        if (looksLikeDate(value)) score -= DATE_PENALTY
        if (looksLikeTime(value, context)) score -= TIME_PENALTY
        if (looksLikePhoneOrLongReference(value)) score -= LONG_REFERENCE_PENALTY
        if (looksLikeUrlOrQueryValue(value, context)) score -= URL_QUERY_PENALTY
        if (looksLikePercent(value, context)) score -= PERCENT_PENALTY
        if (looksLikeMoneyContext(context)) score -= MONEY_CONTEXT_PENALTY
        if (looksLikeDecimalMoneyAmount(value, context)) score -= DECIMAL_MONEY_PENALTY

        return score
    }

    private fun hasOtpPunctuationPattern(value: String, context: String): Boolean {
        val escaped = Regex.escape(value.lowercase())
        return listOf(
            Regex("""(?:code|otp|pin|kod|koodi|codigo|codice|tan)\s*(?:is|=|:|-)?\s*$escaped\b"""),
            Regex("""\b$escaped\s*(?:is|=|-)?\s*(?:your|the|din|ditt|tu|su|il tuo|dein|sinun).{0,14}(?:code|otp|pin|kod|koodi|codigo|codice|tan)"""),
            Regex("""(?:is|=|:|-)\s*$escaped\b"""),
        ).any { it.containsMatchIn(context) }
    }

    private fun hasOwnershipPattern(value: String, context: String): Boolean {
        val escaped = Regex.escape(value.lowercase())
        return Regex("""(?:your|the|din|ditt|tu|su|il tuo|dein|sinun).{0,24}$escaped\b|\b$escaped.{0,24}(?:your|the|din|ditt|tu|su|il tuo|dein|sinun)""")
            .containsMatchIn(context)
    }

    private fun hasExpiryOrSharingSignal(context: String): Boolean {
        return listOf(
            "do not share",
            "don't share",
            "dont share",
            "never share",
            "expires",
            "expire",
            "valid for",
            "minutes",
            "minute",
            "minuter",
            "minuuttia",
        ).any { it in context }
    }

    private fun isShortOtpLikeMessage(normalizedSms: String): Boolean {
        val hasOtpSignal = containsAnyKeyword(normalizedSms, strongPositive) || containsAnyKeyword(normalizedSms, weakPositive)
        return hasOtpSignal && normalizedSms.length <= 180
    }

    private fun containsAnyKeyword(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            Regex("""(?<![a-z0-9])${Regex.escape(keyword)}(?![a-z0-9])""").containsMatchIn(text)
        }
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
    }

    private fun appearsBeforeUrl(value: String, sms: String): Boolean {
        val candidateIndex = sms.indexOf(value, ignoreCase = true)
        if (candidateIndex < 0) return false
        val urlIndex = Regex("""https?://|www\.|/[a-z0-9_-]+|\?[a-z0-9_-]+=""", RegexOption.IGNORE_CASE)
            .find(sms)
            ?.range
            ?.first
            ?: return false
        return candidateIndex < urlIndex
    }

    private fun isRepeated(value: String, sms: String): Boolean {
        return Regex("""(?<![a-z0-9])${Regex.escape(value)}(?![a-z0-9])""", RegexOption.IGNORE_CASE)
            .findAll(sms)
            .take(2)
            .count() > 1
    }

    private fun hasTransactionOtpPattern(value: String, normalizedSms: String): Boolean {
        val escaped = Regex.escape(value.lowercase())
        return Regex("""\botp\b.{0,90}\b(?:is|:)\s*$escaped\b|\b$escaped\b.{0,90}\botp\b""")
            .containsMatchIn(normalizedSms)
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
        return listOf("eur", "usd", "$", "€", "â‚¬", "amount", "paid", "payment", "maksamassa", "tilille").any { it in context }
    }

    private fun looksLikeDecimalMoneyAmount(value: String, context: String): Boolean {
        return Regex("""\b${Regex.escape(value)}[,.]\d{2}\b""").containsMatchIn(context)
    }

    private fun looksLikeTime(value: String, context: String): Boolean {
        if (!value.all(Char::isDigit) || value.length !in 4..6) return false
        return Regex("""\b\d{1,2}[:.]\d{2}\b|\b${Regex.escape(value)}\s*(?:am|pm)\b""").containsMatchIn(context)
    }

    private fun looksLikeUrlOrQueryValue(value: String, context: String): Boolean {
        val escaped = Regex.escape(value.lowercase())
        return Regex("""(?:id|token|ref|utm_[a-z]+|code)=$escaped\b|/$escaped(?:\b|[/?#])""")
            .containsMatchIn(context)
    }

    private fun looksLikePercent(value: String, context: String): Boolean {
        return Regex("""\b${Regex.escape(value)}\s*%""").containsMatchIn(context)
    }
}
