package com.jss.smsotpextractor.otp

import kotlinx.coroutines.runBlocking
import com.jss.smsotpextractor.ModelBenchmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OtpPipelineTest {
    @Test
    fun extractsLoginCodeCandidate() {
        val candidates = CandidateExtractor.extract("Your login code is 123456. Ref 20260605")

        assertEquals("123456", candidates[0].value)
        assertEquals("20260605", candidates[1].value)
    }

    @Test
    fun penalizesDateReferenceCandidate() {
        val sms = "Your login code is 123456. Ref 20260605"
        val scored = HeuristicScorer.score(sms, CandidateExtractor.extract(sms))

        assertEquals("123456", scored.topCandidate?.value)
        assertTrue(scored.candidates.first { it.value == "123456" }.score > scored.candidates.first { it.value == "20260605" }.score)
    }

    @Test
    fun ignoresDigitsEmbeddedInSenderName() = runBlocking {
        val sms = "RandomApp12345: Your verification code is 483920. Ref 20260605."
        val candidates = CandidateExtractor.extract(sms)

        assertEquals(listOf("483920", "20260605"), candidates.map { it.value })

        val ai = FakeAiSelector(AiOtpResult(true, 0, 0.95, """{"is_2fa":true,"candidate_index":0,"confidence":0.95}"""))
        val decision = OtpProcessor(ai).process(sms)

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertEquals("483920", detected.code)
    }

    @Test
    fun rejectsMessagesWithNoCandidates() = runBlocking {
        val decision = OtpProcessor(FakeAiSelector()).process("hello there")

        assertIs<OtpDecision.NoOtp>(decision)
        assertEquals("no_candidates", decision.reason)
    }

    @Test
    fun rejectsNonOtpMessages() = runBlocking {
        val decision = OtpProcessor(FakeAiSelector()).process("Your balance is 123456 EUR")

        assertIs<OtpDecision.NoOtp>(decision)
        assertEquals("negative_keyword", decision.reason)
    }

    @Test
    fun usesHeuristicDirectlyWhenStrong() = runBlocking {
        val ai = FakeAiSelector()
        val decision = OtpProcessor(ai).process("Your login code is 123456. Ref 20260605")

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertEquals("123456", detected.code)
        assertEquals("heuristic", detected.source)
        assertFalse(ai.called)
    }

    @Test
    fun callsAiWhenHeuristicAmbiguous() = runBlocking {
        val ai = FakeAiSelector(AiOtpResult(true, 1, 0.95, """{"is_2fa":true,"candidate_index":1,"confidence":0.95}"""))
        val decision = OtpProcessor(ai).process("2FA values: 111111111 and 222222222")

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertTrue(ai.called)
        assertEquals("222222222", detected.code)
        assertEquals("ai", detected.source)
    }

    @Test
    fun rejectsInvalidAiJsonAndFallsBackWhenHeuristicGoodEnough() = runBlocking {
        val ai = FakeAiSelector(AiOtpResult(false, null, 0.0, "not json"))
        val decision = OtpProcessor(ai).process("2FA values: 111111 and 222222")

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertEquals("heuristic_fallback", detected.source)
    }

    @Test
    fun rejectsOutOfRangeAiIndex() {
        runBlocking {
        val ai = FakeAiSelector(AiOtpResult(true, 99, 0.95, """{"is_2fa":true,"candidate_index":99,"confidence":0.95}"""))
        val decision = OtpProcessor(ai).process("2FA values: 111111111 and 222222222")

        assertIs<OtpDecision.NoOtp>(decision)
        }
    }

    @Test
    fun rejectsLowConfidenceAiResult() {
        runBlocking {
        val ai = FakeAiSelector(AiOtpResult(true, 0, 0.4, """{"is_2fa":true,"candidate_index":0,"confidence":0.4}"""))
        val decision = OtpProcessor(ai).process("2FA values: 111111111 and 222222222")

        assertIs<OtpDecision.NoOtp>(decision)
        }
    }

    @Test
    fun noModelAiResultStillAllowsHeuristicFallback() = runBlocking {
        val ai = FakeAiSelector(AiOtpResult(false, null, 0.0, "AI unavailable: no LiteRT model imported"))
        val decision = OtpProcessor(ai).process("2FA values: 111111 and 222222")

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertEquals("heuristic_fallback", detected.source)
        assertTrue(detected.aiRawOutput?.contains("AI unavailable") == true)
    }

    @Test
    fun benchmarkWarnsForUnparseableModelOutput() {
        val warning = ModelBenchmark.classifyForTests(
            parseableCount = 1,
            correctCount = 3,
            averageLatencyMs = 100.0,
        )

        assertEquals("This model may not follow the required output format.", warning)
    }

    @Test
    fun benchmarkWarnsForSlowModel() {
        val warning = ModelBenchmark.classifyForTests(
            parseableCount = 3,
            correctCount = 3,
            averageLatencyMs = 2_501.0,
        )

        assertEquals("This model may be slow for SMS processing.", warning)
    }

    @Test
    fun benchmarkWarnsForLowAccuracyModel() {
        val warning = ModelBenchmark.classifyForTests(
            parseableCount = 3,
            correctCount = 1,
            averageLatencyMs = 100.0,
        )

        assertEquals("This model may reduce detection accuracy.", warning)
    }

    @Test
    fun candidateOnlyMessagesReachAiForNonEnglishSms() = runBlocking {
        val ai = FakeAiSelector(AiOtpResult(true, 0, 0.95, """{"is_2fa":true,"candidate_index":0,"confidence":0.95}"""))
        val decision = OtpProcessor(ai).process("Votre cle temporaire est 123456. Ref 20260605.")

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertTrue(ai.called)
        assertEquals("123456", detected.code)
        assertEquals("ai", detected.source)
    }

    @Test
    fun multilingualOtpKeywordsCanBeHandledByHeuristic() = runBlocking {
        val cases = listOf(
            "Din verifieringskod ar 123456.",
            "Tu codigo de verificacion es 654321.",
            "Sinun koodi on 482913.",
            "Dein TAN ist 928374.",
        )

        cases.forEach { sms ->
            val ai = FakeAiSelector()
            val detected = assertIs<OtpDecision.OtpDetected>(OtpProcessor(ai).process(sms), "Expected OTP for: $sms")
            assertFalse(ai.called, "Expected heuristic decision for: $sms")
            assertEquals("heuristic", detected.source)
        }
    }

    @Test
    fun structuralPatternsBoostCodesWithoutDependingOnExactKeywordOrder() = runBlocking {
        val ai = FakeAiSelector()
        val detected = assertIs<OtpDecision.OtpDetected>(
            OtpProcessor(ai).process("482913 is your sign-in code. It expires in 5 minutes."),
        )

        assertEquals("482913", detected.code)
        assertEquals("heuristic", detected.source)
        assertFalse(ai.called)
    }

    @Test
    fun prefersCodeBeforeUrlQueryReference() = runBlocking {
        val decision = OtpProcessor(CorpusFakeAiSelector()).process(
            "Your login code is 482913. Continue at https://example.test/login?id=20260605",
        )

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertEquals("482913", detected.code)
    }

    @Test
    fun rejectsReferenceHeavyShippingMessages() = runBlocking {
        val decision = OtpProcessor(FakeAiSelector()).process(
            "Your order 482913 has shipped. Tracking 999888777 will update soon.",
        )

        assertIs<OtpDecision.NoOtp>(decision)
        assertEquals("negative_keyword", decision.reason)
    }

    @Test
    fun rejectsUrlOnlyQueryCodeReferences() = runBlocking {
        val decision = OtpProcessor(FakeAiSelector()).process(
            "View your receipt at https://example.test/receipt?code=482913",
        )

        assertIs<OtpDecision.NoOtp>(decision)
        assertEquals("negative_keyword", decision.reason)
    }

    @Test
    fun parsesAiJsonFromNoisyOutput() {
        val parsed = AiOtpJsonParser.parse("""noise {"is_2fa":true,"candidate_index":0,"confidence":0.95} tail""")

        assertEquals(AiOtpResult(true, 0, 0.95, rawOutput = """noise {"is_2fa":true,"candidate_index":0,"confidence":0.95} tail"""), parsed)
    }

    @Test
    fun sampleSmsCorpusHoldsUp() = runBlocking {
        val cases = listOf(
            SampleCase(
                sms = "Your Twitch verification code is: 902015",
                expectedCode = "902015",
            ),
            SampleCase(
                sms = "Olet maksamassa palvelussamme 100000,00 € tilille DE0000000000000ista tiedot ja vahvista mobiilisovelluksessa koodilla 3183. OP",
                expectedCode = "3183",
            ),
            SampleCase(
                sms = "Your Link verification code is: 550040. To stop receiving these messages, visit support.link.com/sms-opt-out?id=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                expectedCode = "550040",
            ),
            SampleCase(
                sms = "Your Trade Republic verification code is: 4800. Don't share this code with anyone; our employees will never ask for the code.",
                expectedCode = "4800",
            ),
            SampleCase(
                sms = "Your Whop verification code is: 750740",
                expectedCode = "750740",
            ),
            SampleCase(
                sms = "FedEx verification code is 334455.",
                expectedCode = "334455",
            ),
            SampleCase(
                sms = "Steam: To disable or move your Mobile Authenticator use code: 97729",
                expectedCode = "97729",
            ),
            SampleCase(
                sms = "The code to disable or move your Steam Authenticator is: 98446",
                expectedCode = "98446",
            ),
            SampleCase(
                sms = "Confirm your phone number on Wise with the code 813168. Don't share this code with anyone. DAT4apdbQQQk",
                expectedCode = "813168",
            ),
            SampleCase(
                sms = "Din verifieringskod ar 120045. Dela den inte med nagon.",
                expectedCode = "120045",
            ),
            SampleCase(
                sms = "Tu codigo para iniciar sesion es 770088.",
                expectedCode = "770088",
            ),
            SampleCase(
                sms = "482913 is your sign-in code. It expires in 5 minutes.",
                expectedCode = "482913",
            ),
            SampleCase(
                sms = "Use 554433 to login. Never share this code.",
                expectedCode = "554433",
            ),
            SampleCase(
                sms = "Your order 482913 has shipped. Tracking 999888777 will update soon.",
                expectedCode = null,
            ),
            SampleCase(
                sms = "Your appointment is confirmed for 1200 on 20260605.",
                expectedCode = null,
            ),
            SampleCase(
                sms = "Receipt 482913: paid 49.99 EUR. Balance 120000.",
                expectedCode = null,
            ),
        )

        cases.forEach { case ->
            val decision = OtpProcessor(CorpusFakeAiSelector()).process(case.sms)
            if (case.expectedCode == null) {
                assertIs<OtpDecision.NoOtp>(decision, "Expected no OTP for: ${case.sms}")
            } else {
                val detected = assertIs<OtpDecision.OtpDetected>(decision, "Expected OTP for: ${case.sms}")
                assertEquals(case.expectedCode, detected.code, "Wrong OTP for: ${case.sms}")
            }
        }
    }

    private data class SampleCase(
        val sms: String,
        val expectedCode: String?,
    )

    private class CorpusFakeAiSelector : AiOtpSelector {
        override suspend fun select(sms: String, candidates: List<OtpCandidate>): AiOtpResult {
            val hasOtpSignal = listOf("otp", "code", "verification", "verify", "2fa", "passcode")
                .any { it in sms.lowercase() }
            if (sms.contains("FedEx", ignoreCase = true) && !hasOtpSignal) {
                return AiOtpResult(false, null, 0.0, """{"is_2fa":false,"candidate_index":null,"confidence":0}""")
            }
            val top = candidates.maxByOrNull { it.score }
            return AiOtpResult(
                is2fa = top != null,
                candidateIndex = top?.index,
                confidence = if (top != null) 0.95 else 0.0,
                rawOutput = """{"is_2fa":true,"candidate_index":${top?.index},"confidence":0.95}""",
            )
        }
    }

    private class FakeAiSelector(
        private val result: AiOtpResult = AiOtpResult(false, null, 0.0, """{"is_2fa":false,"candidate_index":null,"confidence":0}"""),
    ) : AiOtpSelector {
        var called = false

        override suspend fun select(sms: String, candidates: List<OtpCandidate>): AiOtpResult {
            called = true
            return result
        }
    }
}
