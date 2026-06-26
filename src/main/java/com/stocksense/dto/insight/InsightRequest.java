package com.stocksense.dto.insight;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A natural-language question (Bangla or English) for the Insight Agent. */
public record InsightRequest(
        @NotBlank(message = "question is required")
        @Size(max = 1000, message = "question is too long")
        String question) {}
