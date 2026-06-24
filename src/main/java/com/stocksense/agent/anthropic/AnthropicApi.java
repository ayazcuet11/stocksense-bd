package com.stocksense.agent.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Minimal record model of the Anthropic Messages API (https://docs.anthropic.com/en/api/messages).
 *
 * We talk to the API directly via {@link AnthropicClient} rather than Spring AI, because Spring AI
 * has no stable Spring Boot 4 release yet. This is enough to support the tool-calling loop the
 * Festival Demand Agent needs.
 */
public final class AnthropicApi {

    private AnthropicApi() {}

    /** A function the model may call. {@code inputSchema} is a JSON-Schema object. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tool(String name,
                       String description,
                       @JsonProperty("input_schema") Map<String, Object> inputSchema) {}

    /**
     * One content block. The Messages API is polymorphic; rather than a class hierarchy we use a
     * single record with nullable fields and NON_NULL serialization so each block only emits the
     * keys that apply to its {@code type} (text / tool_use / tool_result).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            String type,
            String text,
            String id,
            String name,
            Object input,
            @JsonProperty("tool_use_id") String toolUseId,
            Object content) {

        public static ContentBlock text(String text) {
            return new ContentBlock("text", text, null, null, null, null, null);
        }

        public static ContentBlock toolResult(String toolUseId, String content) {
            return new ContentBlock("tool_result", null, null, null, null, toolUseId, content);
        }
    }

    /** A conversation turn. {@code content} is either a String or a List&lt;ContentBlock&gt;. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(String role, Object content) {
        public static Message user(String text) { return new Message("user", text); }
        public static Message user(List<ContentBlock> blocks) { return new Message("user", blocks); }
        public static Message assistant(List<ContentBlock> blocks) { return new Message("assistant", blocks); }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MessagesRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<Tool> tools,
            List<Message> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessagesResponse(
            String id,
            String role,
            List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason) {}
}
