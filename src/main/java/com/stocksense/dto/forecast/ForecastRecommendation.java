package com.stocksense.dto.forecast;

/** A single product stock-up recommendation produced by the Festival Demand Agent. */
public record ForecastRecommendation(
        Long productId,
        String sku,
        String productName,
        String festivalName,
        String festivalDate,
        Integer currentStock,
        Double baselineDailyUnits,
        Double expectedDailyUnits,
        Integer recommendedStockUpUnits,
        Double upliftPct,
        String reasoning) {
}
