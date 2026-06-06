package com.jss.smsotpextractor.otp

import java.text.Normalizer

object OtpPrefilter {
    private val positiveKeywords = listOf(
        "otp",
        "code",
        "pin",
        "verification",
        "verifiering",
        "verifieringskod",
        "verify",
        "verifica",
        "verificar",
        "verificacion",
        "verifizierung",
        "confirma",
        "confirmar",
        "confirme",
        "login",
        "inloggning",
        "kirjautuminen",
        "sign in",
        "signin",
        "sign-in",
        "2fa",
        "two-factor",
        "authentication",
        "passcode",
        "one-time",
        "security code",
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

    private val specificOtpPositiveKeywords = positiveKeywords - listOf("code", "pin", "kod", "codigo", "codice")

    private val negativeKeywords = listOf(
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
        "order",
        "ticket",
        "case",
        "paid",
        "payment",
        "charged",
        "coupon",
    )

    fun classify(sms: String, candidates: List<OtpCandidate>): PrefilterResult {
        if (candidates.isEmpty()) {
            return PrefilterResult(possibleOtp = false, reason = "no_candidates")
        }

        val normalized = normalizeText(sms)
        if (candidates.all { looksLikeUrlReference(it.value, normalized) }) {
            return PrefilterResult(possibleOtp = false, reason = "url_reference")
        }

        val keywordText = stripUrlNoise(normalized)
        val hasPositive = containsAnyKeyword(keywordText, positiveKeywords)
        val hasSpecificPositive = containsAnyKeyword(keywordText, specificOtpPositiveKeywords)
        val hasNegative = containsAnyKeyword(keywordText, negativeKeywords)
        if (hasNegative && !hasSpecificPositive) {
            return PrefilterResult(possibleOtp = false, reason = "negative_keyword")
        }

        if (hasPositive) {
            return PrefilterResult(possibleOtp = true, reason = "otp_keyword")
        }

        return PrefilterResult(possibleOtp = true, reason = "candidate_only")
    }

    private fun stripUrlNoise(text: String): String {
        return text
            .replace(Regex("""https?://\S+|www\.\S+"""), " ")
            .replace(Regex("""[?&][a-z0-9_-]+=[^\s&]+"""), " ")
    }

    private fun containsAnyKeyword(text: String, keywords: List<String>): Boolean {
        return keywords.any { keyword ->
            Regex("""(?<![a-z0-9])${Regex.escape(keyword)}(?![a-z0-9])""").containsMatchIn(text)
        }
    }

    private fun looksLikeUrlReference(value: String, normalizedSms: String): Boolean {
        val escaped = Regex.escape(value.lowercase())
        return Regex("""(?:[?&][a-z0-9_-]+=$escaped\b|(?<![a-z0-9])(?:id|token|ref|session)=$escaped\b|/$escaped(?:\b|[/?#]))""")
            .containsMatchIn(normalizedSms)
    }

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
    }
}
