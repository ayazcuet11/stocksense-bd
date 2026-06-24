package com.stocksense.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.agent.anthropic.AnthropicApi;
import com.stocksense.agent.anthropic.AnthropicClient;
import com.stocksense.agent.tools.ReorderAgentTools;
import com.stocksense.config.AnthropicProperties;
import com.stocksense.domain.Branch;
import com.stocksense.domain.BranchStock;
import com.stocksense.domain.Product;
import com.stocksense.repository.AgentDecisionRepository;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.BranchStockRepository;
import com.stocksense.repository.ProductRepository;
import com.stocksense.repository.SaleRepository;
import com.stocksense.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Smart Reorder Agent (Phase 4). For a branch, it looks at products that have crossed their reorder
 * threshold, sizes a reorder quantity (informed by the Phase 3 festival signal), picks a supplier by
 * price/lead-time, and drafts purchase orders into PENDING_APPROVAL — never further. A manager then
 * approves via {@code POST /api/purchase-orders/{id}/approve} (human-in-the-loop gate).
 *
 * <p>This runs off the request thread: it is invoked by a RabbitMQ listener, not the controller.
 */
@Service
public class SmartReorderAgent {

    private static final Logger log = LoggerFactory.getLogger(SmartReorderAgent.class);
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    private static final String AGENT_TYPE = "SMART_REORDER";
    private static final String FORECAST_AGENT_TYPE = "FESTIVAL_DEMAND";

    private static final String SYSTEM_PROMPT = """
            You are the Smart Reorder Agent for StockSense BD. Some products at a branch have dropped to
            or below their reorder threshold. Your job: draft purchase orders to replenish them, sized
            for upcoming festival demand, choosing a good supplier.

            Method:
            1. Call getUpcomingFestivals to see what is coming inside the horizon.
            2. For each low-stock product, estimate the demand to cover until restock arrives (supplier
               lead time) plus any festival surge — use getSalesHistory on last year's matching window
               vs a normal baseline to gauge the surge.
            3. Call compareSupplierPrices to pick a supplier (cheaper and shorter lead time is better).
            4. Call createDraftPurchaseOrder once per supplier, with a line per product, using that
               supplier's unit price. Order enough to clear the threshold and cover the surge, but do
               not over-order slow movers.
            5. When finished, call finishReorderRun with a one-paragraph summary.

            Rules:
            - Only draft orders for the low-stock products listed in the context.
            - Every order is created as PENDING_APPROVAL for a human to approve — never assume approval.
            - Be specific in the summary: which products, quantities, suppliers and the festival driver.
            """;

    private final AnthropicClient client;
    private final AnthropicProperties props;
    private final ReorderAgentTools tools;
    private final AgentAuditService audit;
    private final BranchRepository branchRepository;
    private final BranchStockRepository branchStockRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final AgentDecisionRepository decisionRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public SmartReorderAgent(AnthropicClient client, AnthropicProperties props, ReorderAgentTools tools,
                             AgentAuditService audit, BranchRepository branchRepository,
                             BranchStockRepository branchStockRepository, ProductRepository productRepository,
                             SaleRepository saleRepository, AgentDecisionRepository decisionRepository) {
        this.client = client;
        this.props = props;
        this.tools = tools;
        this.audit = audit;
        this.branchRepository = branchRepository;
        this.branchStockRepository = branchStockRepository;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.decisionRepository = decisionRepository;
    }

    /** Runs the reorder scan for a branch. Side effects: drafted POs + an AgentDecision audit row. */
    public void scan(Long branchId, int horizonDays) {
        Long tenantId = TenantContext.get();
        Branch branch = branchRepository.findByIdAndTenantId(branchId, tenantId).orElse(null);
        if (branch == null) {
            log.warn("Reorder scan skipped — branch {} not found for tenant {}", branchId, tenantId);
            return;
        }

        List<BranchStock> lowStock = branchStockRepository.findLowStockByBranchId(branchId);
        if (lowStock.isEmpty()) {
            audit.record(AGENT_TYPE,
                    "branch=%s (id=%d)".formatted(branch.getName(), branchId),
                    "No products below reorder threshold — nothing to draft.", "[]", null);
            log.info("Reorder scan for branch {}: no low stock", branchId);
            return;
        }

        Map<Long, Product> productById = productRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<AnthropicApi.Message> messages = new ArrayList<>();
        messages.add(AnthropicApi.Message.user(buildContext(branch, horizonDays, lowStock, productById)));

        List<Long> draftedPoIds = new ArrayList<>();
        String summary = runToolLoop(messages, draftedPoIds);

        audit.record(AGENT_TYPE,
                "branch=%s (id=%d), lowStock=%d items, horizonDays=%d"
                        .formatted(branch.getName(), branchId, lowStock.size(), horizonDays),
                (summary == null ? "Run complete." : summary) + " — drafted PO ids: " + draftedPoIds,
                "draftedPurchaseOrderIds=" + draftedPoIds, null);
        log.info("Reorder scan for branch {} drafted {} PO(s): {}", branchId, draftedPoIds.size(), draftedPoIds);
    }

    private String runToolLoop(List<AnthropicApi.Message> messages, List<Long> draftedPoIds) {
        String summary = null;
        for (int i = 0; i < props.getMaxToolIterations(); i++) {
            AnthropicApi.MessagesResponse resp = client.createMessage(new AnthropicApi.MessagesRequest(
                    props.getModel(), props.getMaxTokens(), SYSTEM_PROMPT, tools.specs(), messages));

            List<AnthropicApi.ContentBlock> content = resp.content() == null ? List.of() : resp.content();
            messages.add(AnthropicApi.Message.assistant(content));

            List<AnthropicApi.ContentBlock> toolUses = content.stream()
                    .filter(b -> "tool_use".equals(b.type())).toList();
            if (toolUses.isEmpty()) {
                return summary != null ? summary : textOf(content);
            }

            List<AnthropicApi.ContentBlock> results = new ArrayList<>();
            boolean finished = false;
            for (AnthropicApi.ContentBlock b : toolUses) {
                if (ReorderAgentTools.FINISH.equals(b.name())) {
                    JsonNode in = mapper.valueToTree(b.input());
                    summary = in.path("summary").asText(summary);
                    finished = true;
                    results.add(AnthropicApi.ContentBlock.toolResult(b.id(), "{\"ok\":true}"));
                    continue;
                }
                JsonNode input = mapper.valueToTree(b.input());
                String result = tools.dispatch(b.name(), input);
                if (ReorderAgentTools.CREATE_DRAFT_PO.equals(b.name())) {
                    collectPoId(result, draftedPoIds);
                }
                results.add(AnthropicApi.ContentBlock.toolResult(b.id(), result));
            }
            if (finished) {
                return summary;
            }
            messages.add(AnthropicApi.Message.user(results));
        }
        log.warn("Reorder agent hit max iterations without finishing");
        return summary;
    }

    private void collectPoId(String toolResultJson, List<Long> draftedPoIds) {
        try {
            JsonNode node = mapper.readTree(toolResultJson);
            if (node.has("purchaseOrderId") && !node.get("purchaseOrderId").isNull()) {
                draftedPoIds.add(node.get("purchaseOrderId").asLong());
            }
        } catch (Exception e) {
            log.debug("Could not parse PO id from tool result: {}", e.getMessage());
        }
    }

    private String buildContext(Branch branch, int horizonDays, List<BranchStock> lowStock,
                                Map<Long, Product> productById) {
        StringBuilder sb = new StringBuilder();
        sb.append("Today is ").append(LocalDate.now(DHAKA)).append(".\n");
        sb.append("Branch: ").append(branch.getName()).append(" (id=").append(branch.getId())
                .append("), city ").append(branch.getCity()).append(".\n");
        sb.append("Planning horizon: next ").append(horizonDays).append(" days.\n\n");

        sb.append("Products at or below reorder threshold (productId | name | unit | onHand | reorderAt):\n");
        for (BranchStock s : lowStock) {
            Product p = productById.get(s.getProductId());
            sb.append(s.getProductId()).append(" | ")
                    .append(p == null ? "?" : p.getName()).append(" | ")
                    .append(p == null ? "?" : p.getUnit()).append(" | ")
                    .append(s.getQuantity()).append(" | ")
                    .append(s.getReorderThreshold()).append("\n");
        }

        decisionRepository.findFirstByAgentTypeOrderByCreatedAtDesc(FORECAST_AGENT_TYPE).ifPresent(d ->
                sb.append("\nMost recent festival forecast (for context): ")
                        .append(d.getOutputSummary()).append("\n"));

        var earliest = saleRepository.findEarliestSoldAt(branch.getId());
        var latest = saleRepository.findLatestSoldAt(branch.getId());
        sb.append("\nHistorical sales are available ");
        if (earliest != null && latest != null) {
            sb.append("from ").append(LocalDate.ofInstant(earliest, DHAKA)).append(" to ")
                    .append(LocalDate.ofInstant(latest, DHAKA)).append("; query within that range. ");
        } else {
            sb.append("(none). ");
        }
        sb.append("Start by calling getUpcomingFestivals(withinDays=").append(horizonDays).append(").");
        return sb.toString();
    }

    private String textOf(List<AnthropicApi.ContentBlock> content) {
        return content.stream()
                .filter(b -> "text".equals(b.type()) && b.text() != null)
                .map(AnthropicApi.ContentBlock::text)
                .collect(Collectors.joining(" "));
    }
}
