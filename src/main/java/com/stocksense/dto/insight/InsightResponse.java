package com.stocksense.dto.insight;

import java.time.Instant;

/**
 * The Insight Agent's reply: the natural-language answer, the (read-only) SQL it ran, the tabular
 * result, and the {@code AgentDecision} id for the audit trail.
 */
public record InsightResponse(
        String question,
        String answer,
        String sql,
        QueryResultView result,
        Long decisionId,
        Instant generatedAt) {}
