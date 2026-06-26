package com.stocksense;

import com.stocksense.agent.sql.SqlGuard;
import com.stocksense.config.InsightProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Phase 5 Text-to-SQL guardrails. No Spring context — the guard is pure logic over
 * the {@link InsightProperties} allowlist. These pin down the safety contract: read-only SELECT only,
 * single statement, allowlisted tables, enforced LIMIT.
 */
class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard(new InsightProperties());

    @Test
    void allowsSimpleSelectAndAppendsLimit() {
        String out = guard.sanitize("SELECT name FROM products WHERE tenant_id = 1");
        assertThat(out).contains("LIMIT 200");
    }

    @Test
    void keepsExistingLimit() {
        String out = guard.sanitize("SELECT name FROM products LIMIT 5");
        assertThat(out).isEqualTo("SELECT name FROM products LIMIT 5");
        assertThat(out).doesNotContain("LIMIT 200");
    }

    @Test
    void allowsCteAndJoins() {
        String sql = "WITH t AS (SELECT branch_id, SUM(total_amount) s FROM sales GROUP BY branch_id) "
                + "SELECT b.name, t.s FROM t JOIN branches b ON b.id = t.branch_id WHERE b.tenant_id = 1";
        assertThat(guard.sanitize(sql)).contains("LIMIT 200");
    }

    @Test
    void rejectsNonSelect() {
        assertThatThrownBy(() -> guard.sanitize("UPDATE products SET name = 'x'"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.sanitize("DELETE FROM products"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.sanitize("DROP TABLE products"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWriteKeywordHidingAfterSelect() {
        assertThatThrownBy(() -> guard.sanitize("SELECT * FROM products INTO OUTFILE '/tmp/x'"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("into");
    }

    @Test
    void rejectsStackedStatements() {
        assertThatThrownBy(() -> guard.sanitize("SELECT 1 FROM products; DROP TABLE products"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsComments() {
        assertThatThrownBy(() -> guard.sanitize("SELECT name FROM products -- drop"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.sanitize("SELECT name FROM products /* x */"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonAllowlistedTable() {
        assertThatThrownBy(() -> guard.sanitize("SELECT email, password_hash FROM app_users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app_users");
    }

    @Test
    void rejectsSchemaQualifiedSystemTable() {
        assertThatThrownBy(() -> guard.sanitize("SELECT * FROM mysql.user"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.sanitize("SELECT * FROM information_schema.tables"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsQueryWithoutFrom() {
        assertThatThrownBy(() -> guard.sanitize("SELECT 1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsTrailingSemicolon() {
        assertThat(guard.sanitize("SELECT id FROM branches;")).doesNotContain(";");
    }
}
