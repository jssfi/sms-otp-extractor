package com.jss.smsotpextractor

object ModelBenchmarkClassifier {
    private const val SLOW_AVERAGE_MS = 2_500.0

    fun classifyForTests(
        parseableCount: Int,
        correctCount: Int,
        averageLatencyMs: Double,
    ): String? {
        return when {
            parseableCount < 2 -> "This model may not follow the required output format."
            averageLatencyMs > SLOW_AVERAGE_MS -> "This model may be slow for SMS processing."
            correctCount < 2 -> "This model may reduce detection accuracy."
            else -> null
        }
    }
}
