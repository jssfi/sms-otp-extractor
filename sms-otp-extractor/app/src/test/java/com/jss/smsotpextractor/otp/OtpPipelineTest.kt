package com.jss.smsotpextractor.otp

import kotlinx.coroutines.runBlocking
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
    fun candidateOnlyMessagesReachAiForNonEnglishSms() = runBlocking {
        val ai = FakeAiSelector(AiOtpResult(true, 0, 0.95, """{"is_2fa":true,"candidate_index":0,"confidence":0.95}"""))
        val decision = OtpProcessor(ai).process("Vahvistuskoodisi on 123456. Viite 20260605.")

        val detected = assertIs<OtpDecision.OtpDetected>(decision)
        assertTrue(ai.called)
        assertEquals("123456", detected.code)
        assertEquals("ai", detected.source)
    }

    @Test
    fun parsesAiJsonFromNoisyOutput() {
        val parsed = AiOtpJsonParser.parse("""noise {"is_2fa":true,"candidate_index":0,"confidence":0.95} tail""")

        assertEquals(AiOtpResult(true, 0, 0.95, rawOutput = """noise {"is_2fa":true,"candidate_index":0,"confidence":0.95} tail"""), parsed)
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
