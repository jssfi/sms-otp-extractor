package com.jss.smsotpextractor.otp

object CandidateExtractor {
    private val numericCandidate = Regex("""(?i)(?<![a-z0-9])\d{4,10}(?![a-z0-9])""")
    private val alphaNumericCandidate = Regex("""(?i)(?<![a-z0-9])[a-z0-9]{6,12}(?![a-z0-9])""")

    fun extract(sms: String): List<OtpCandidate> {
        val matches = buildList {
            addAll(numericCandidate.findAll(sms))
            addAll(alphaNumericCandidate.findAll(sms).filter { match ->
                val value = match.value
                value.any(Char::isDigit) && value.any(Char::isLetter)
            })
        }.sortedBy { it.range.first }

        val seen = linkedSetOf<String>()
        return matches.mapNotNull { match ->
            val value = match.value.trim()
            if (!seen.add(value.lowercase())) {
                null
            } else {
                OtpCandidate(
                    index = seen.size - 1,
                    value = value,
                    context = contextAround(sms, match.range.first, match.range.last),
                )
            }
        }
    }

    private fun contextAround(sms: String, start: Int, end: Int): String {
        val contextStart = (start - CONTEXT_CHARS).coerceAtLeast(0)
        val contextEnd = (end + 1 + CONTEXT_CHARS).coerceAtMost(sms.length)
        return sms.substring(contextStart, contextEnd).replace(Regex("""\s+"""), " ").trim()
    }

    private const val CONTEXT_CHARS = 28
}
