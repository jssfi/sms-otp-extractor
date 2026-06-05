package com.jss.smsotpextractor.otp

class OtpProcessor(
    private val aiSelector: AiOtpSelector,
) {
    suspend fun process(sms: String): OtpDecision {
        val startedAt = now()

        val extractionStartedAt = now()
        val candidates = CandidateExtractor.extract(sms)
        val extractionEndedAt = now()
        if (candidates.isEmpty()) {
            return OtpDecision.NoOtp(
                reason = "no_candidates",
                timings = OtpTimings(
                    candidateExtractionMs = elapsedMs(extractionStartedAt, extractionEndedAt),
                    totalMs = elapsedMs(startedAt, now()),
                ),
            )
        }

        val prefilterStartedAt = now()
        val prefilter = OtpPrefilter.classify(sms, candidates)
        val prefilterEndedAt = now()
        if (!prefilter.possibleOtp) {
            return OtpDecision.NoOtp(
                reason = prefilter.reason,
                candidates = candidates,
                timings = OtpTimings(
                    candidateExtractionMs = elapsedMs(extractionStartedAt, extractionEndedAt),
                    prefilterMs = elapsedMs(prefilterStartedAt, prefilterEndedAt),
                    totalMs = elapsedMs(startedAt, now()),
                ),
            )
        }

        val heuristicStartedAt = now()
        val scored = HeuristicScorer.score(sms, candidates)
        val heuristicEndedAt = now()
        val top = scored.topCandidate
        if (top != null && scored.topScore >= 70 && scored.margin >= 25) {
            return detected(
                code = top.value,
                source = "heuristic",
                selectedCandidate = top,
                scored = scored,
                startedAt = startedAt,
                extractionStartedAt = extractionStartedAt,
                extractionEndedAt = extractionEndedAt,
                prefilterStartedAt = prefilterStartedAt,
                prefilterEndedAt = prefilterEndedAt,
                heuristicStartedAt = heuristicStartedAt,
                heuristicEndedAt = heuristicEndedAt,
            )
        }

        val aiStartedAt = now()
        val aiResult = aiSelector.select(sms, scored.candidates)
        val aiEndedAt = now()
        if (
            aiResult.is2fa &&
            aiResult.candidateIndex != null &&
            aiResult.confidence >= 0.75 &&
            aiResult.candidateIndex in candidates.indices
        ) {
            val selected = candidates[aiResult.candidateIndex]
            return OtpDecision.OtpDetected(
                code = selected.value,
                source = "ai",
                selectedCandidate = selected,
                candidates = scored.candidates,
                timings = timings(
                    startedAt,
                    extractionStartedAt,
                    extractionEndedAt,
                    prefilterStartedAt,
                    prefilterEndedAt,
                    heuristicStartedAt,
                    heuristicEndedAt,
                    aiStartedAt,
                    aiEndedAt,
                    aiResult,
                ),
                aiRawOutput = aiResult.rawOutput,
            )
        }

        if (top != null && scored.topScore >= 60) {
            return OtpDecision.OtpDetected(
                code = top.value,
                source = "heuristic_fallback",
                selectedCandidate = top,
                candidates = scored.candidates,
                timings = timings(
                    startedAt,
                    extractionStartedAt,
                    extractionEndedAt,
                    prefilterStartedAt,
                    prefilterEndedAt,
                    heuristicStartedAt,
                    heuristicEndedAt,
                    aiStartedAt,
                    aiEndedAt,
                    aiResult,
                ),
                aiRawOutput = aiResult.rawOutput,
            )
        }

        return OtpDecision.NoOtp(
            reason = "low_confidence",
            candidates = scored.candidates,
            timings = timings(
                startedAt,
                extractionStartedAt,
                extractionEndedAt,
                prefilterStartedAt,
                prefilterEndedAt,
                heuristicStartedAt,
                heuristicEndedAt,
                aiStartedAt,
                aiEndedAt,
                aiResult,
            ),
            aiRawOutput = aiResult.rawOutput,
        )
    }

    private fun detected(
        code: String,
        source: String,
        selectedCandidate: OtpCandidate,
        scored: ScoredCandidates,
        startedAt: Long,
        extractionStartedAt: Long,
        extractionEndedAt: Long,
        prefilterStartedAt: Long,
        prefilterEndedAt: Long,
        heuristicStartedAt: Long,
        heuristicEndedAt: Long,
    ): OtpDecision.OtpDetected {
        return OtpDecision.OtpDetected(
            code = code,
            source = source,
            selectedCandidate = selectedCandidate,
            candidates = scored.candidates,
            timings = OtpTimings(
                candidateExtractionMs = elapsedMs(extractionStartedAt, extractionEndedAt),
                prefilterMs = elapsedMs(prefilterStartedAt, prefilterEndedAt),
                heuristicMs = elapsedMs(heuristicStartedAt, heuristicEndedAt),
                totalMs = elapsedMs(startedAt, now()),
            ),
        )
    }

    private fun timings(
        startedAt: Long,
        extractionStartedAt: Long,
        extractionEndedAt: Long,
        prefilterStartedAt: Long,
        prefilterEndedAt: Long,
        heuristicStartedAt: Long,
        heuristicEndedAt: Long,
        aiStartedAt: Long,
        aiEndedAt: Long,
        aiResult: AiOtpResult,
    ): OtpTimings {
        return OtpTimings(
            candidateExtractionMs = elapsedMs(extractionStartedAt, extractionEndedAt),
            prefilterMs = elapsedMs(prefilterStartedAt, prefilterEndedAt),
            heuristicMs = elapsedMs(heuristicStartedAt, heuristicEndedAt),
            aiMs = elapsedMs(aiStartedAt, aiEndedAt),
            totalMs = elapsedMs(startedAt, now()),
            aiFirstTokenMs = aiResult.firstTokenMs,
            aiPromptToLastTokenMs = aiResult.promptToLastTokenMs,
        )
    }

    private fun now(): Long = System.nanoTime()

    private fun elapsedMs(start: Long, end: Long): Double {
        return (end - start) / 1_000_000.0
    }
}
