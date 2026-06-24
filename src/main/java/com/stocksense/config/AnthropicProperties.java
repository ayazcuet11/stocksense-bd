package com.stocksense.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Anthropic Claude client used by the agentic layer (Phase 3+).
 * The API key MUST come from the environment (ANTHROPIC_API_KEY) — never hardcode it.
 */

@Data
@Component
@ConfigurationProperties(prefix = "app.anthropic")
public class AnthropicProperties {

    /** Master switch — when false (or no API key), agent endpoints return 503. */
    private boolean enabled = true;

    /** Anthropic API key. Injected from env var ANTHROPIC_API_KEY. */
    private String apiKey;

    private String baseUrl = "https://api.anthropic.com";

    /** Model id. Overridable via ANTHROPIC_MODEL. */
    private String model = "claude-sonnet-4-6";

    private int maxTokens = 4096;

    /** HTTP read timeout — agent turns can take a while. */
    private int timeoutSeconds = 120;

    /** Safety cap on the tool-calling loop to bound cost/latency. */
    private int maxToolIterations = 8;

}
