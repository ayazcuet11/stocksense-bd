package com.stocksense.dto.insight;

import java.util.List;

/** Tabular result returned to the UI: column headers and aligned rows. */
public record QueryResultView(List<String> columns, List<List<Object>> rows, int rowCount, boolean truncated) {

    public static QueryResultView empty() {
        return new QueryResultView(List.of(), List.of(), 0, false);
    }
}
