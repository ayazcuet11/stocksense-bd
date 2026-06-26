package com.stocksense.agent.sql;

import java.util.List;

/**
 * The outcome of a read-only query: column names and rows (each row a list of JSON-friendly values,
 * aligned to {@code columns}). {@code truncated} is true when more rows existed than the row cap.
 */
public record QueryResult(List<String> columns, List<List<Object>> rows, int rowCount, boolean truncated) {

    public static QueryResult empty() {
        return new QueryResult(List.of(), List.of(), 0, false);
    }
}
