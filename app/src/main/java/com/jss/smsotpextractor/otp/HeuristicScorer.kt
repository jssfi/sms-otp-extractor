package com.jss.smsotpextractor.otp

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
        "tracking",
        "shipment",
        "delivery",
        "paid",
        "payment",
    )

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

        if (containsAnyKeyword(context, strongPositive)) score += 35
        if (containsAnyKeyword(context, weakPositive)) score += 15
        if (containsAnyKeyword(normalizedSms, strongPositive)) score += 8
        if (hasOtpPunctuationPattern(value, context)) score += 18
        if (hasOwnershipPattern(value, context)) score += 16
        if (hasExpiryOrSharingSignal(context)) score += 12
        if (isShortOtpLikeMessage(normalizedSms)) score += 8
        if (appearsBeforeUrl(value, sms)) score += 8
        if (isRepeated(value, sms)) score += 10

        if (referenceWords.any { word -> Regex("""(?<![a-z0-9])$word\W+${Regex.escape(value.lowercase())}\b""").containsMatchIn(context) }) score -= 30
        if (containsAnyKeyword(context, referenceWords) && !containsAnyKeyword(context, strongPositive)) score -= 12
        if (containsAnyKeyword(context, hardNegativeWords) && !containsAnyKeyword(context, strongPositive)) score -= 22
        if (looksLikeDate(value)) score -= 45
        if (looksLikeTime(value, context)) score -= 30
        if (looksLikePhoneOrLongReference(value)) score -= 35
        if (looksLikeUrlOrQueryValue(value, context)) score -= 35
        if (looksLikePercent(value, context)) score -= 25
        if (looksLikeMoneyContext(context)) score -= 20
        if (looksLikeDecimalMoneyAmount(value, context)) score -= 50

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
