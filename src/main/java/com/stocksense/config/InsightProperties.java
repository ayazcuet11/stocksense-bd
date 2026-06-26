package com.stocksense.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for the Insight Agent (Phase 5 — Text-to-SQL).
 *
 * <p>Safety knobs the brief mandates: a dedicated read-only DB user, a table allowlist, a statement
 * timeout, and a hard row cap. The read-only credentials come from the environment in production
 * ({@code INSIGHT_DB_USERNAME}/{@code INSIGHT_DB_PASSWORD}); when unset they fall back to the primary
 * datasource user, but the connection is still opened read-only and every statement is guarded.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.insight")
public class InsightProperties {

    /**
     * Read-only DB username. Best practice: a MySQL user granted only {@code SELECT} on the
     * allowlisted tables. Falls back to the primary datasource user when blank.
     */
    private String dbUsername = "";

    /** Read-only DB password. Falls back to the primary datasource password when blank. */
    private String dbPassword = "";

    /** Per-query statement timeout (seconds). Caps run-away or accidentally heavy queries. */
    private int queryTimeoutSeconds = 8;

    /** Hard cap on rows returned to the model and the UI. */
    private int maxRows = 200;

    /** Cap on the tool-calling loop (model may retry a failed query a couple of times). */
    private int maxToolIterations = 6;

    /**
     * Tables the generated SQL may read. Deliberately excludes {@code app_users} (password hashes /
     * PII) and {@code agent_decisions} (internal audit). Any query touching a table outside this list
     * is rejected before it reaches the database.
     */
    private List<String> allowedTables = List.of(
            "tenants",
            "branches",
            "categories",
            "products",
            "branch_stocks",
            "suppliers",
            "product_suppliers",
            "sales",
            "sale_lines",
            "purchase_orders",
            "purchase_order_lines",
            "festival_events");
}
