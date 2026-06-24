package com.stocksense.agent.anthropic;

import com.stocksense.config.AnthropicProperties;
import com.stocksense.exception.AgentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

import static java.util.Objects.nonNull;

/** Thin wrapper over the Anthropic Messages endpoint. */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AnthropicProperties props;
    private final RestClient http;

    public AnthropicClient(AnthropicProperties props) {
        this.props = props;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(15).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(props.getTimeoutSeconds()).toMillis());

        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** True when the agent layer is switched on and an API key is present. */
    public boolean isEnabled() {
        return props.isEnabled()
                && nonNull(props.getApiKey())
                && !props.getApiKey().isBlank();
    }

    public AnthropicApi.MessagesResponse createMessage(AnthropicApi.MessagesRequest request) {

        if (!isEnabled())
            throw new AgentUnavailableException(
                    "AI agent is not configured. Set the ANTHROPIC_API_KEY environment variable.");
        try {
            return http.post()
                    .uri("/v1/messages")
                    .header("x-api-key", props.getApiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AnthropicApi.MessagesResponse.class);

        } catch (RestClientResponseException ex) {
            log.error("Anthropic API error {}: {}"
                    , ex.getStatusCode()
                    , ex.getResponseBodyAsString());

            throw new AgentUnavailableException("AI provider returned "
                    + ex.getStatusCode().value()
                    + ". Check API key and model id.");

        } catch (Exception ex) {
            log.error("Anthropic API call failed", ex);
            throw new AgentUnavailableException("AI provider is unreachable: " + ex.getMessage());
        }
    }
}
