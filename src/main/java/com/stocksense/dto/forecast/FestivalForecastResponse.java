package com.stocksense.dto.forecast;

import java.time.Instant;
import java.util.List;

/** Result of a Festival Demand Agent run for one branch and horizon. */
public record FestivalForecastResponse(
        Long branchId,
        String branchName,
        int horizonDays,
        Instant generatedAt,
        Long decisionId,
        String summary,
        List<ForecastRecommendation> recommendations) {
}
