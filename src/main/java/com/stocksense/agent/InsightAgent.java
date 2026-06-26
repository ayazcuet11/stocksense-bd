package com.stocksense.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.agent.anthropic.AnthropicApi;
import com.stocksense.agent.anthropic.AnthropicClient;
import com.stocksense.agent.sql.QueryResult;
import com.stocksense.agent.tools.InsightAgentTools;
import com.stocksense.config.AnthropicProperties;
import com.stocksense.domain.AgentDecision;
import com.stocksense.dto.insight.InsightResponse;
import com.stocksense.dto.insight.QueryResultView;
import com.stocksense.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Insight Agent (Phase 5) — Text-to-SQL over the StockSense database.
 *
 * <p>The user asks a question in Bangla or English; the model writes a read-only {@code SELECT},
 * runs it through {@link InsightAgentTools#runQuery} (which is guarded and executed on the dedicated
 * read-only datasource), reads the rows, and submits a natural-language answer. Every run is written
 * to the {@code agent_decisions} audit log.
 *
 * <p>Agentic pattern: <b>Text-to-SQL with guardrails</b>. The model never touches a write-capable
 * connection; the {@code SqlGuard} rejects anything that isn't a single allowlisted SELECT, and the
 * read-only datasource + statement timeout + row cap are the last lines of defense.
 */
@Service
public class InsightAgent {

    private static final Logger log = LoggerFactory.getLogger(InsightAgent.class);
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final String AGENT_TYPE = "INSIGHT_SQL";

    private static final String SYSTEM_PROMPT = """
            You are the Insight Agent for StockSense BD, an inventory system for retail chains in
            Bangladesh. You answer business questions by writing and running READ-ONLY SQL over the
            company database, then explaining the result in plain language.

            Rules — follow strictly:
            - Generate exactly ONE MySQL SELECT (or WITH ... SELECT) at a time. Never write, update or
              delete; never use any statement other than SELECT. Such queries will be rejected.
            - Only use the tables described in the context. Do not reference any other table.
            - This is a MULTI-TENANT database. You are answering for tenant_id = %d ONLY. Every query
              must be scoped to that tenant: filter tenant_id = %d on tenant-owned tables, and for
              branch-scoped tables (sales, sale_lines, branch_stocks) join through branches (or
              products) and filter that tenant_id. Never return another tenant's data.
            - Call runSqlQuery to execute. If it returns an error, read it, fix the SQL, and retry.
            - Keep result sets small and relevant (aggregate, group, order, limit).
            - When you have the answer, call submitInsight ONCE with a clear answer in the SAME
              language as the user's question (Bangla if the question is Bangla), plus the SQL you used.
            - If the question cannot be answered from the available tables, say so via submitInsight.
            """;

    private final AnthropicClient client;
    private final AnthropicProperties props;
    private final InsightAgentTools tools;
    private final AgentAuditService audit;
    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();

    public InsightAgent(AnthropicClient client, AnthropicProperties props, InsightAgentTools tools,
                        AgentAuditService audit, RateLimiter rateLimiter) {
        this.client = client;
        this.props = props;
        this.tools = tools;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    public InsightResponse ask(String question) {
        Long tenantId = TenantContext.get();
        rateLimiter.check("insight", tenantId, 10);

        String system = SYSTEM_PROMPT.formatted(tenantId, tenantId);
        List<AnthropicApi.Message> messages = new ArrayList<>();
        messages.add(AnthropicApi.Message.user(buildContext(question, tenantId)));

        Outcome outcome = runToolLoop(messages, system);

        QueryResultView resultView = outcome.result == null
                ? QueryResultView.empty()
                : new QueryResultView(outcome.result.columns(), outcome.result.rows(),
                        outcome.result.rowCount(), outcome.result.truncated());

        String answer = outcome.answer != null ? outcome.answer : "I couldn't produce an answer.";

        AgentDecision decision = audit.record(
                AGENT_TYPE,
                "tenant=%d, question=%s".formatted(tenantId, truncate(question, 500)),
                truncate(answer, 1000) + (outcome.result != null ? " — " + outcome.result.rowCount() + " row(s)" : ""),
                outcome.sql != null ? outcome.sql : "(no query run)",
                null);

        return new InsightResponse(question, answer, outcome.sql, resultView, decision.getId(), Instant.now());
    }

    /** Drives the conversation until the model submits an answer or we hit the iteration cap. */
    private Outcome runToolLoop(List<AnthropicApi.Message> messages, String system) {
        Outcome outcome = new Outcome();
        for (int i = 0; i < props.getMaxToolIterations(); i++) {
            AnthropicApi.MessagesResponse resp = client.createMessage(new AnthropicApi.MessagesRequest(
                    props.getModel(), props.getMaxTokens(), system, tools.specs(), messages));

            List<AnthropicApi.ContentBlock> content = resp.content() == null ? List.of() : resp.content();
            messages.add(AnthropicApi.Message.assistant(content));

            List<AnthropicApi.ContentBlock> toolUses = content.stream()
                    .filter(b -> "tool_use".equals(b.type())).toList();
            if (toolUses.isEmpty()) {
                // Model answered in prose without submitting — capture it as a fallback answer.
                String text = textOf(content);
                if (outcome.answer == null && !text.isBlank()) outcome.answer = text;
                return outcome;
            }

            List<AnthropicApi.ContentBlock> results = new ArrayList<>();
            boolean finished = false;
            for (AnthropicApi.ContentBlock b : toolUses) {
                JsonNode input = mapper.valueToTree(b.input());
                if (InsightAgentTools.SUBMIT_INSIGHT.equals(b.name())) {
                    outcome.answer = input.path("answer").asText(outcome.answer);
                    String sql = input.path("sql").asText(null);
                    if (sql != null && !sql.isBlank()) outcome.sql = sql;
                    results.add(AnthropicApi.ContentBlock.toolResult(b.id(), "{\"ok\":true}"));
                    finished = true;
                } else if (InsightAgentTools.RUN_SQL_QUERY.equals(b.name())) {
                    results.add(AnthropicApi.ContentBlock.toolResult(b.id(),
                            runQuery(input.path("sql").asText(""), outcome)));
                } else {
                    results.add(AnthropicApi.ContentBlock.toolResult(b.id(),
                            "{\"error\":\"Unknown tool\"}"));
                }
            }
            if (finished) return outcome;
            messages.add(AnthropicApi.Message.user(results));
        }
        log.warn("Insight agent hit max iterations without submitting");
        return outcome;
    }

    /** Run one query, recording the last successful SQL + result on the outcome. */
    private String runQuery(String rawSql, Outcome outcome) {
        try {
            InsightAgentTools.Executed executed = tools.runQuery(rawSql);
            outcome.sql = executed.sql();
            outcome.result = executed.result();
            return tools.resultToJson(executed.result());
        } catch (IllegalArgumentException e) {
            // Rejected by the guard or failed to execute — let the model read the reason and retry.
            log.info("Insight query rejected: {}", e.getMessage());
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String buildContext(String question, Long tenantId) {
        return "Today is " + LocalDate.now(DHAKA) + ".\n"
                + "You are answering for tenant_id = " + tenantId + " only.\n\n"
                + tools.schemaDescription() + "\n"
                + "User question: " + question;
    }

    private String textOf(List<AnthropicApi.ContentBlock> content) {
        StringBuilder sb = new StringBuilder();
        for (AnthropicApi.ContentBlock b : content) {
            if ("text".equals(b.type()) && b.text() != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(b.text());
            }
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Mutable accumulator for the loop's final answer/SQL/result. */
    private static final class Outcome {
        String answer;
        String sql;
        QueryResult result;
    }
}
