package com.stocksense.agent.sql;

import com.stocksense.config.InsightProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and sanitises model-generated SQL before it is allowed anywhere near the database
 * (Phase 5 guardrail). The contract, per the brief:
 *
 * <ul>
 *   <li>Exactly one statement, and it must be a read-only {@code SELECT} (or {@code WITH ... SELECT}).</li>
 *   <li>No DML/DDL/admin keywords, no SQL comments, no stacked statements.</li>
 *   <li>Every referenced table must be on the {@link InsightProperties#getAllowedTables() allowlist}.</li>
 *   <li>A {@code LIMIT} is enforced so an unbounded query can't dump the whole table.</li>
 * </ul>
 *
 * Treat the model's SQL as strictly as any other untrusted external input. On any violation this
 * throws {@link IllegalArgumentException} with a message the agent can read and retry against.
 */
@Component
public class SqlGuard {

    /** Statement-type / admin keywords that must never appear in a read-only query. */
    private static final Set<String> FORBIDDEN = Set.of(
            "insert", "update", "delete", "drop", "alter", "create", "truncate", "rename",
            "grant", "revoke", "replace", "merge", "call", "exec", "execute", "do",
            "into", "load_file", "outfile", "dumpfile", "infile",
            "set", "use", "lock", "unlock", "handler", "prepare", "deallocate",
            "sleep", "benchmark", "get_lock");

    /** {@code FROM <table>} / {@code JOIN <table>} — captures the (optionally schema-qualified) name. */
    private static final Pattern TABLE_REF =
            Pattern.compile("(?:\\bfrom|\\bjoin)\\s+([`\"]?[a-zA-Z_][a-zA-Z0-9_$]*[`\"]?(?:\\.[`\"]?[a-zA-Z_][a-zA-Z0-9_$]*[`\"]?)?)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern WORD = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    /** {@code <name> AS (} — a CTE definition; such names are virtual, not real tables. */
    private static final Pattern CTE_NAME =
            Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)\\s+as\\s*\\(", Pattern.CASE_INSENSITIVE);

    private final InsightProperties props;

    public SqlGuard(InsightProperties props) {
        this.props = props;
    }

    /**
     * @return the sanitised SQL (semicolon stripped, {@code LIMIT} ensured), ready to execute.
     * @throws IllegalArgumentException if the SQL violates any guardrail.
     */
    public String sanitize(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("Empty SQL.");
        }
        String sql = rawSql.trim();

        // No comments — they can smuggle keywords or stacked statements past simple checks.
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/") || sql.contains("#")) {
            throw new IllegalArgumentException("SQL comments are not allowed.");
        }

        // Allow exactly one trailing semicolon; reject any stacked statements.
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.contains(";")) {
            throw new IllegalArgumentException("Only a single statement is allowed (no ';').");
        }

        String lower = sql.toLowerCase(Locale.ROOT);

        // Must be a read-only query.
        if (!(lower.startsWith("select") || lower.startsWith("with"))) {
            throw new IllegalArgumentException("Only SELECT (or WITH ... SELECT) queries are allowed.");
        }

        // No write/admin keywords anywhere (whole-word match so e.g. 'created_at' is fine).
        Matcher w = WORD.matcher(lower);
        while (w.find()) {
            if (FORBIDDEN.contains(w.group())) {
                throw new IllegalArgumentException(
                        "Forbidden keyword '" + w.group() + "' — only read-only SELECT queries are allowed.");
            }
        }

        // Every referenced table must be on the allowlist (CTE names defined in a WITH are virtual).
        Set<String> referenced = referencedTables(sql);
        if (referenced.isEmpty()) {
            throw new IllegalArgumentException("Query must read from at least one table (FROM clause required).");
        }
        Set<String> cteNames = cteNames(sql);
        for (String table : referenced) {
            if (!cteNames.contains(table) && !props.getAllowedTables().contains(table)) {
                throw new IllegalArgumentException(
                        "Table '" + table + "' is not allowed. Allowed tables: " + props.getAllowedTables());
            }
        }

        // Ensure a row cap so an unbounded query can't dump an entire table.
        if (!lower.matches("(?s).*\\blimit\\b.*")) {
            sql = sql + " LIMIT " + props.getMaxRows();
        }
        return sql;
    }

    /** Tables named after FROM/JOIN, schema prefix and quoting stripped, lower-cased. */
    private Set<String> referencedTables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher m = TABLE_REF.matcher(sql);
        while (m.find()) {
            String ident = m.group(1).replace("`", "").replace("\"", "");
            int dot = ident.lastIndexOf('.');
            if (dot >= 0) {
                ident = ident.substring(dot + 1); // strip schema qualifier (e.g. mysql.user -> user)
            }
            tables.add(ident.toLowerCase(Locale.ROOT));
        }
        return tables;
    }

    /** Names introduced by a WITH clause (e.g. {@code WITH t AS (SELECT ...)}), lower-cased. */
    private Set<String> cteNames(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = CTE_NAME.matcher(sql);
        while (m.find()) {
            names.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return names;
    }
}
