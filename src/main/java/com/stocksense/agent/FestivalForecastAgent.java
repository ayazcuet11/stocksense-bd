package com.stocksense.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.agent.anthropic.AnthropicApi;
import com.stocksense.agent.anthropic.AnthropicClient;
import com.stocksense.agent.tools.FestivalAgentTools;
import com.stocksense.config.AnthropicProperties;
import com.stocksense.domain.AgentDecision;
import com.stocksense.domain.Branch;
import com.stocksense.domain.BranchStock;
import com.stocksense.domain.Product;
import com.stocksense.dto.forecast.FestivalForecastResponse;
import com.stocksense.dto.forecast.ForecastRecommendation;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.BranchStockRepository;
import com.stocksense.repository.ProductRepository;
import com.stocksense.repository.SaleRepository;
import com.stocksense.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Festival Demand Agent (Phase 3). Given a branch and a horizon, it identifies products likely to
 * surge before upcoming festivals and recommends stock-up quantities with reasoning.
 *
 * <p>Agentic pattern: <b>tool/function calling</b>. The model drives the analysis by calling the
 * tools in {@link FestivalAgentTools} (sales history, upcoming festivals, current stock) and ends by
 * calling {@code submitFestivalForecast} with structured recommendations. Every run is written to
 * the {@code agent_decisions} audit log.
 */
@Service
public class FestivalForecastAgent {

    private static final Logger log = LoggerFactory.getLogger(FestivalForecastAgent.class);
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final String AGENT_TYPE = "FESTIVAL_DEMAND";

    private static final String SYSTEM_PROMPT = """
            You are the Festival Demand Agent for StockSense BD, an inventory system for retail chains
            in Bangladesh. Your job: for a given branch and planning horizon, find products that will
            surge before upcoming festivals/seasons (Eid ul-Fitr, Eid ul-Adha, Durga Puja, Pohela
            Boishakh, mango season, hilsa/monsoon season) and recommend how much extra stock to hold.

            Method — follow it:
            1. Call getUpcomingFestivals to see which events fall inside the horizon.
            2. For products plausibly tied to those events (e.g. sweet drinks/Rooh Afza, vermicelli,
               sugar, dates, spices, ghee before Eid; fish/hilsa in monsoon; mangoes in summer),
               estimate the surge by calling getSalesHistory for the SAME festival window LAST YEAR
               and comparing it to a normal (non-festival) baseline window for the same product.
            3. Use getCurrentStock (or the on-hand figures already given) to see the gap.
            4. Compute a recommended extra stock-up quantity that covers the expected surge above what
               is already on hand, accounting for the days until the festival.

            Rules:
            - Only recommend products that show a real signal in the data. Quality over quantity —
              4 to 10 well-justified recommendations is ideal. Do not pad the list.
            - Quantify: give baseline vs expected daily units and an uplift %.
            - Keep each reasoning to 1-2 sentences a shop manager would understand.
            - When done, call submitFestivalForecast exactly once. Do not answer in prose.
            """;

    private final AnthropicClient client;
    private final AnthropicProperties props;
    private final FestivalAgentTools tools;
    private final AgentAuditService audit;
    private final RateLimiter rateLimiter;
    private final BranchRepository branchRepository;
    private final BranchStockRepository branchStockRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    // Vanilla mapper — agent payloads carry no java.time values; keeps us off the shared HTTP mapper.
    private final ObjectMapper mapper = new ObjectMapper();

    public FestivalForecastAgent(AnthropicClient client, AnthropicProperties props,
                                 FestivalAgentTools tools, AgentAuditService audit, RateLimiter rateLimiter,
                                 BranchRepository branchRepository, BranchStockRepository branchStockRepository,
                                 ProductRepository productRepository, SaleRepository saleRepository) {
        this.client = client;
        this.props = props;
        this.tools = tools;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
        this.branchRepository = branchRepository;
        this.branchStockRepository = branchStockRepository;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
    }

    public FestivalForecastResponse forecast(Long branchId, int horizonDays) {
        Long tenantId = TenantContext.get();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchId));
        rateLimiter.check("festival-forecast", tenantId, 6);

        List<Product> products = productRepository.findAllByTenantId(tenantId);
        Map<Long, Product> productById = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<AnthropicApi.Message> messages = new ArrayList<>();
        messages.add(AnthropicApi.Message.user(buildContext(branch, horizonDays, products)));

        SubmitPayload payload = runToolLoop(messages);

        List<ForecastRecommendation> recs = toRecommendations(payload, productById);
        String summary = payload != null ? payload.summary() : "No forecast produced.";

        AgentDecision decision = audit.record(
                AGENT_TYPE,
                "branch=%s (id=%d), horizonDays=%d, catalog=%d products"
                        .formatted(branch.getName(), branchId, horizonDays, products.size()),
                summary + " — " + recs.size() + " recommendation(s)",
                writeJson(recs),
                null);

        return new FestivalForecastResponse(branchId, branch.getName(), horizonDays,
                Instant.now(), decision.getId(), summary, recs);
    }

    /** Drives the conversation until the model submits a forecast or we hit the iteration cap. */
    private SubmitPayload runToolLoop(List<AnthropicApi.Message> messages) {
        for (int i = 0; i < props.getMaxToolIterations(); i++) {
            AnthropicApi.MessagesResponse resp = client.createMessage(new AnthropicApi.MessagesRequest(
                    props.getModel(), props.getMaxTokens(), SYSTEM_PROMPT, tools.specs(), messages));

            List<AnthropicApi.ContentBlock> content =
                    resp.content() == null ? List.of() : resp.content();
            messages.add(AnthropicApi.Message.assistant(content));

            List<AnthropicApi.ContentBlock> toolUses = content.stream()
                    .filter(b -> "tool_use".equals(b.type())).toList();

            // Final structured answer?
            for (AnthropicApi.ContentBlock b : toolUses) {
                if (FestivalAgentTools.SUBMIT_FORECAST.equals(b.name())) {
                    return mapper.convertValue(b.input(), SubmitPayload.class);
                }
            }
            if (toolUses.isEmpty()) {
                log.warn("Festival agent stopped without submitting (stop_reason={})", resp.stopReason());
                return null;
            }

            // Execute data tools and feed the results back.
            List<AnthropicApi.ContentBlock> results = new ArrayList<>();
            for (AnthropicApi.ContentBlock b : toolUses) {
                JsonNode input = mapper.valueToTree(b.input());
                String result = tools.dispatch(b.name(), input);
                results.add(AnthropicApi.ContentBlock.toolResult(b.id(), result));
            }
            messages.add(AnthropicApi.Message.user(results));
        }
        log.warn("Festival agent hit max iterations without submitting");
        return null;
    }

    private String buildContext(Branch branch, int horizonDays, List<Product> products) {
        Map<Long, BranchStock> stockByProduct = branchStockRepository.findAllByBranchId(branch.getId())
                .stream().collect(Collectors.toMap(BranchStock::getProductId, s -> s, (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        sb.append("Today is ").append(LocalDate.now(DHAKA)).append(".\n");
        sb.append("Branch: ").append(branch.getName()).append(" (id=").append(branch.getId())
                .append("), city ").append(branch.getCity()).append(".\n");
        sb.append("Planning horizon: next ").append(horizonDays).append(" days.\n\n");
        sb.append("Product catalogue with current on-hand stock at this branch ")
                .append("(productId | sku | name | unit | onHand | reorderAt):\n");
        for (Product p : products) {
            BranchStock s = stockByProduct.get(p.getId());
            sb.append(p.getId()).append(" | ").append(p.getSku()).append(" | ").append(p.getName())
                    .append(" | ").append(p.getUnit())
                    .append(" | ").append(s == null ? 0 : s.getQuantity())
                    .append(" | ").append(s == null ? 0 : s.getReorderThreshold())
                    .append("\n");
        }
        Instant earliest = saleRepository.findEarliestSoldAt(branch.getId());
        Instant latest = saleRepository.findLatestSoldAt(branch.getId());
        sb.append("\nIMPORTANT — historical sales for this branch are available ONLY for the window ");
        if (earliest != null && latest != null) {
            sb.append(LocalDate.ofInstant(earliest, DHAKA)).append(" to ")
                    .append(LocalDate.ofInstant(latest, DHAKA)).append(". ");
        } else {
            sb.append("(none found). ");
        }
        sb.append("To estimate a festival surge, call getSalesHistory for the matching calendar window ")
                .append("WITHIN that available range (e.g. the weeks around the same festival in that year) ")
                .append("and compare it to a normal baseline window for the same product.\n");
        sb.append("Start by calling getUpcomingFestivals(withinDays=").append(horizonDays).append(").");
        return sb.toString();
    }

    private List<ForecastRecommendation> toRecommendations(SubmitPayload payload, Map<Long, Product> productById) {
        if (payload == null || payload.recommendations() == null) return List.of();
        List<ForecastRecommendation> out = new ArrayList<>();
        for (RecItem r : payload.recommendations()) {
            Product p = r.productId() == null ? null : productById.get(r.productId());
            out.add(new ForecastRecommendation(
                    r.productId(),
                    p == null ? null : p.getSku(),
                    p == null ? null : p.getName(),
                    r.festivalName(),
                    r.festivalDate(),
                    r.currentStock(),
                    r.baselineDailyUnits(),
                    r.expectedDailyUnits(),
                    r.recommendedStockUpUnits(),
                    r.upliftPct(),
                    r.reasoning()));
        }
        return out;
    }

    private String writeJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    // --- structured output captured from the submit tool -----------------------

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record SubmitPayload(String summary, List<RecItem> recommendations) {}

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record RecItem(
            Long productId,
            String festivalName,
            String festivalDate,
            Integer currentStock,
            Double baselineDailyUnits,
            Double expectedDailyUnits,
            Integer recommendedStockUpUnits,
            Double upliftPct,
            String reasoning) {}
}
