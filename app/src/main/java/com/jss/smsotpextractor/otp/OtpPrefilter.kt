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

    private val negativeKeywords = listOf(
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
        "order",
        "ticket",
        "case",
        "paid",
        "payment",
    )

    fun classify(sms: String, candidates: List<OtpCandidate>): PrefilterResult {
        if (candidates.isEmpty()) {
            return PrefilterResult(possibleOtp = false, reason = "no_candidates")
        }

        val normalized = normalizeText(sms)
        val keywordText = stripUrlNoise(normalized)
        val hasPositive = containsAnyKeyword(keywordText, positiveKeywords)
        val hasNegative = containsAnyKeyword(keywordText, negativeKeywords)
        if (hasNegative && !hasPositive) {
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

    private fun normalizeText(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
    }
}
