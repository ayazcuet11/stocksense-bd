package com.stocksense.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.agent.anthropic.AnthropicApi;
import com.stocksense.agent.sql.QueryResult;
import com.stocksense.agent.sql.ReadOnlyQueryExecutor;
import com.stocksense.agent.sql.SqlGuard;
import com.stocksense.config.InsightProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Function-calling tools for the Insight Agent (Phase 5 — Text-to-SQL). Two tools:
 * <ul>
 *   <li>{@code runSqlQuery} — guard, then execute a read-only SELECT and hand the rows back so the
 *       model can read the answer (or the error, and retry).</li>
 *   <li>{@code submitInsight} — the structured final answer; calling it ends the loop.</li>
 * </ul>
 *
 * The DB schema is advertised to the model up front (see {@link #schemaDescription()}) rather than as
 * a tool, keeping the loop short.
 */
@Component
public class InsightAgentTools {

    public static final String RUN_SQL_QUERY = "runSqlQuery";
    public static final String SUBMIT_INSIGHT = "submitInsight";

    private final SqlGuard sqlGuard;
    private final ReadOnlyQueryExecutor executor;
    private final InsightProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public InsightAgentTools(SqlGuard sqlGuard, ReadOnlyQueryExecutor executor, InsightProperties props) {
        this.sqlGuard = sqlGuard;
        this.executor = executor;
        this.props = props;
    }

    public List<AnthropicApi.Tool> specs() {
        return List.of(
                tool(RUN_SQL_QUERY,
                        "Execute ONE read-only MySQL SELECT against the StockSense database and return the "
                                + "rows. Only SELECT/WITH is permitted; only allowlisted tables; a LIMIT is "
                                + "enforced. If the query is rejected or errors, you get the reason back — fix "
                                + "the SQL and try again.",
                        schema("""
                                {"type":"object","properties":{
                                  "sql":{"type":"string","description":"A single read-only SELECT statement, no trailing semicolon, no comments"}},
                                  "required":["sql"]}""")),
                tool(SUBMIT_INSIGHT,
                        "Submit the final answer to the user's question, in the same language as the "
                                + "question (Bangla or English). Call this exactly once when you have the "
                                + "answer. Include the SELECT you relied on.",
                        schema("""
                                {"type":"object","properties":{
                                  "answer":{"type":"string","description":"A clear natural-language answer to the question"},
                                  "sql":{"type":"string","description":"The final SELECT used to derive the answer"}},
                                  "required":["answer"]}"""))
        );
    }

    /** Guard + execute. Throws {@link IllegalArgumentException} (rejected/failed) so the caller can
     *  feed the reason back to the model. The returned SQL is the sanitised form actually run. */
    public Executed runQuery(String rawSql) {
        String sanitized = sqlGuard.sanitize(rawSql);
        QueryResult result = executor.execute(sanitized);
        return new Executed(sanitized, result);
    }

    /** Compact JSON of a result for feeding back to the model. */
    public String resultToJson(QueryResult result) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "columns", result.columns(),
                    "rowCount", result.rowCount(),
                    "truncated", result.truncated(),
                    "rows", result.rows()));
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    /** A human-readable description of the readable schema, embedded in the system context. */
    public String schemaDescription() {
        return """
                Database: MySQL. Readable tables (you may ONLY query these — %s):

                tenants(id, name, created_at)
                branches(id, tenant_id, name, address, city, phone)
                categories(id, tenant_id, name)
                products(id, tenant_id, sku, name, category_id, unit, vat_rate)
                branch_stocks(id, branch_id, product_id, quantity, reorder_threshold, updated_at)
                suppliers(id, tenant_id, name, phone, lead_time_days)
                product_suppliers(product_id, supplier_id, unit_price)
                sales(id, branch_id, sold_at, total_amount, vat_amount)
                sale_lines(id, sale_id, product_id, quantity, unit_price)
                purchase_orders(id, tenant_id, branch_id, supplier_id, status, created_by_agent, approved_by, created_at)
                purchase_order_lines(id, po_id, product_id, quantity, unit_price)
                festival_events(id, name, type, gregorian_date, hijri_date)

                Relationships: branches.tenant_id->tenants.id; products.category_id->categories.id;
                branch_stocks join branches(branch_id) and products(product_id); sales.branch_id->branches.id;
                sale_lines.sale_id->sales.id and sale_lines.product_id->products.id;
                product_suppliers joins products and suppliers; purchase_order_lines.po_id->purchase_orders.id.
                Note: branch-scoped tables (sales, sale_lines, branch_stocks) have no tenant_id column —
                reach the tenant through a join to branches (or products).
                The app_users and agent_decisions tables are NOT readable.
                """.formatted(props.getAllowedTables());
    }

    // --- helpers ---------------------------------------------------------------

    public record Executed(String sql, QueryResult result) {}

    private AnthropicApi.Tool tool(String name, String description, Map<String, Object> schema) {
        return new AnthropicApi.Tool(name, description, schema);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schema(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Bad tool schema", e);
        }
    }
}
