package com.stocksense.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * A dedicated, read-only {@link DataSource} used <b>only</b> by the Insight Agent (Phase 5). It points
 * at the same primary database but opens connections {@code read-only} and through a separate pool, so
 * the model-generated SQL can never reach the application's write-capable connections.
 *
 * <p>Defense in depth: this read-only pool + {@link InsightProperties#getDbUsername() a GRANT SELECT
 * user} (recommended in prod) + the application-level {@code SqlGuard} (reject non-SELECT, allowlist,
 * statement timeout, row cap). The pool is lazy ({@code minimumIdle=0},
 * {@code initializationFailTimeout=-1}) so it never opens a connection — or fails app startup — unless
 * the agent is actually used.
 */
@Configuration
public class InsightDataSourceConfig {

    public static final String INSIGHT_DATASOURCE = "insightDataSource";

    @Bean(name = INSIGHT_DATASOURCE)
    public DataSource insightDataSource(
            InsightProperties props,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.driver-class-name:}") String driverClassName,
            @Value("${spring.datasource.username}") String primaryUsername,
            @Value("${spring.datasource.password:}") String primaryPassword) {

        String username = props.getDbUsername().isBlank() ? primaryUsername : props.getDbUsername();
        String password = props.getDbPassword().isBlank() ? primaryPassword : props.getDbPassword();

        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("insight-ro");
        ds.setJdbcUrl(url);
        if (!driverClassName.isBlank()) {
            ds.setDriverClassName(driverClassName);
        }
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(3);
        ds.setMinimumIdle(0);
        // Don't probe the DB at startup — the test profile (H2) and a down DB must not break boot.
        ds.setInitializationFailTimeout(-1);
        return ds;
    }
}
