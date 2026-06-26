package com.stocksense.agent.sql;

import com.stocksense.config.InsightDataSourceConfig;
import com.stocksense.config.InsightProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes an already-{@link SqlGuard sanitised} SELECT against the dedicated read-only datasource.
 *
 * <p>Belt and braces: the connection is opened {@code read-only} (so the server rejects any write),
 * the statement carries a {@link InsightProperties#getQueryTimeoutSeconds() query timeout}, and rows
 * are capped at {@link InsightProperties#getMaxRows()}. Values are converted to JSON-friendly types so
 * they serialise cleanly for both the model and the UI.
 */
@Component
public class ReadOnlyQueryExecutor {

    private final DataSource dataSource;
    private final InsightProperties props;

    public ReadOnlyQueryExecutor(@Qualifier(InsightDataSourceConfig.INSIGHT_DATASOURCE) DataSource dataSource,
                                 InsightProperties props) {
        this.dataSource = dataSource;
        this.props = props;
    }

    public QueryResult execute(String sanitizedSql) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true); // server-enforced: writes fail even if a guard were bypassed
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(props.getQueryTimeoutSeconds());
                stmt.setMaxRows(props.getMaxRows() + 1); // +1 to detect truncation
                try (ResultSet rs = stmt.executeQuery(sanitizedSql)) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            // Surface a concise message — it is fed back to the model so it can fix the query.
            throw new IllegalArgumentException("Query failed: " + e.getMessage());
        }
    }

    private QueryResult map(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        List<String> columns = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            columns.add(md.getColumnLabel(i));
        }

        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= props.getMaxRows()) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.add(toJsonFriendly(rs.getObject(i)));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows, rows.size(), truncated);
    }

    /** Keep numbers/strings/booleans as-is; render temporal/binary/other types as strings. */
    private Object toJsonFriendly(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return value.toString();
    }
}
