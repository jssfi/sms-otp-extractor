package com.jss.smsotpextractor.otp

object CandidateExtractor {
    private val numericCandidate = Regex("""(?i)(?<![a-z0-9])\d{4,10}(?![a-z0-9])""")
    private val separatedNumericCandidate = Regex("""(?i)(?<![a-z0-9])\d{2,4}(?:[ -]\d{2,4}){1,3}(?![a-z0-9])""")
    private val alphaNumericCandidate = Regex("""(?i)(?<![a-z0-9])[a-z0-9]{6,12}(?![a-z0-9])""")

    fun extract(sms: String): List<OtpCandidate> {
        val separatedMatches = separatedNumericCandidate.findAll(sms)
            .mapNotNull { match ->
                val normalizedValue = match.value.filter(Char::isDigit)
                if (normalizedValue.length in 4..10) {
                    ExtractedMatch(
                        value = normalizedValue,
                        start = match.range.first,
                        end = match.range.last,
                    )
                } else {
                    null
                }
            }
            .toList()

        val matches = buildList {
            addAll(separatedMatches)
            addAll(numericCandidate.findAll(sms)
                .filterNot { match -> separatedMatches.any { it.overlaps(match.range) } }
                .map { match ->
                    ExtractedMatch(
                        value = match.value,
                        start = match.range.first,
                        end = match.range.last,
                    )
                })
            addAll(alphaNumericCandidate.findAll(sms)
                .filterNot { match -> separatedMatches.any { it.overlaps(match.range) } }
                .filter { match ->
                    val value = match.value
                    value.any(Char::isDigit) && value.any(Char::isLetter)
                }
                .map { match ->
                    ExtractedMatch(
                        value = match.value,
                        start = match.range.first,
                        end = match.range.last,
                    )
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
                    context = contextAround(sms, match.start, match.end),
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

    private data class ExtractedMatch(
        val value: String,
        val start: Int,
        val end: Int,
    ) {
        val range: IntRange = start..end

        fun overlaps(other: IntRange): Boolean {
            return start <= other.last && other.first <= end
        }
    }
}
