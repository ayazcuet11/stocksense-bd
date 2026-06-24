package com.stocksense.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.agent.anthropic.AnthropicApi;
import com.stocksense.domain.ProductSupplier;
import com.stocksense.domain.PurchaseOrder;
import com.stocksense.domain.Supplier;
import com.stocksense.dto.reorder.DraftLine;
import com.stocksense.repository.ProductSupplierRepository;
import com.stocksense.repository.SupplierRepository;
import com.stocksense.security.TenantContext;
import com.stocksense.service.PurchaseOrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Function-calling tools for the Smart Reorder Agent. Reuses the demand tools from
 * {@link FestivalAgentTools} (sales history, upcoming festivals) so reorder quantities are informed
 * by the same festival signal as Phase 3, and adds the reorder-specific actions:
 * {@code compareSupplierPrices} and {@code createDraftPurchaseOrder}.
 *
 * <p>{@code createDraftPurchaseOrder} is a mutating action, but it only creates a PENDING_APPROVAL
 * order — the irreversible step (approve/send) still requires a human. See {@link PurchaseOrderService}.
 */
@Component
public class ReorderAgentTools {

    public static final String COMPARE_SUPPLIER_PRICES = "compareSupplierPrices";
    public static final String CREATE_DRAFT_PO = "createDraftPurchaseOrder";
    public static final String FINISH = "finishReorderRun";

    private final FestivalAgentTools demandTools;
    private final ProductSupplierRepository productSupplierRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReorderAgentTools(FestivalAgentTools demandTools,
                             ProductSupplierRepository productSupplierRepository,
                             SupplierRepository supplierRepository,
                             PurchaseOrderService purchaseOrderService) {
        this.demandTools = demandTools;
        this.productSupplierRepository = productSupplierRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderService = purchaseOrderService;
    }

    public List<AnthropicApi.Tool> specs() {
        return List.of(
                tool(FestivalAgentTools.GET_SALES_HISTORY,
                        "Units sold and average daily sales for a product at a branch over a date range. "
                                + "Use last year's festival window to size the surge.",
                        schema("""
                                {"type":"object","properties":{
                                  "productId":{"type":"integer"},"branchId":{"type":"integer"},
                                  "fromDate":{"type":"string","description":"yyyy-MM-dd"},
                                  "toDate":{"type":"string","description":"yyyy-MM-dd"}},
                                  "required":["productId","branchId","fromDate","toDate"]}""")),
                tool(FestivalAgentTools.GET_UPCOMING_FESTIVALS,
                        "Festivals/seasonal events within the next N days.",
                        schema("""
                                {"type":"object","properties":{"withinDays":{"type":"integer"}},
                                  "required":["withinDays"]}""")),
                tool(COMPARE_SUPPLIER_PRICES,
                        "List the suppliers that carry a product, with unit price and lead-time days, "
                                + "cheapest first. Use this to choose a supplier before drafting an order.",
                        schema("""
                                {"type":"object","properties":{"productId":{"type":"integer"}},
                                  "required":["productId"]}""")),
                tool(CREATE_DRAFT_PO,
                        "Create a DRAFT purchase order (status PENDING_APPROVAL) for one supplier with one "
                                + "or more lines. A human must approve it before it is sent. Group lines by "
                                + "supplier — call once per supplier.",
                        schema("""
                                {"type":"object","properties":{
                                  "branchId":{"type":"integer"},
                                  "supplierId":{"type":"integer"},
                                  "lines":{"type":"array","items":{"type":"object","properties":{
                                    "productId":{"type":"integer"},
                                    "quantity":{"type":"integer"},
                                    "unitPrice":{"type":"number"}},
                                    "required":["productId","quantity","unitPrice"]}}},
                                  "required":["branchId","supplierId","lines"]}""")),
                tool(FINISH,
                        "Call once when done, with a short summary of what was ordered and why.",
                        schema("""
                                {"type":"object","properties":{"summary":{"type":"string"}},
                                  "required":["summary"]}"""))
        );
    }

    /**
     * Execute a data/action tool. For {@link #CREATE_DRAFT_PO} the returned JSON includes the new
     * {@code purchaseOrderId} so the orchestrator can collect drafted orders.
     */
    public String dispatch(String name, JsonNode input) {
        return switch (name) {
            case FestivalAgentTools.GET_SALES_HISTORY,
                 FestivalAgentTools.GET_UPCOMING_FESTIVALS,
                 FestivalAgentTools.GET_CURRENT_STOCK -> demandTools.dispatch(name, input);
            case COMPARE_SUPPLIER_PRICES -> compareSupplierPrices(input.path("productId").asLong());
            case CREATE_DRAFT_PO -> createDraftPurchaseOrder(input);
            default -> toJson(Map.of("error", "Unknown tool: " + name));
        };
    }

    String compareSupplierPrices(long productId) {
        List<ProductSupplier> options = productSupplierRepository.findAllById_ProductId(productId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductSupplier ps : options) {
            Long supplierId = ps.getId().getSupplierId();
            Supplier s = supplierRepository.findByIdAndTenantId(supplierId, TenantContext.get()).orElse(null);
            if (s == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplierId", supplierId);
            row.put("supplierName", s.getName());
            row.put("unitPrice", ps.getUnitPrice());
            row.put("leadTimeDays", s.getLeadTimeDays());
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> (BigDecimal) r.get("unitPrice")));
        return toJson(Map.of("productId", productId, "suppliers", rows));
    }

    String createDraftPurchaseOrder(JsonNode input) {
        long branchId = input.path("branchId").asLong();
        long supplierId = input.path("supplierId").asLong();
        List<DraftLine> lines = new ArrayList<>();
        for (JsonNode ln : input.path("lines")) {
            lines.add(new DraftLine(
                    ln.path("productId").asLong(),
                    ln.path("quantity").asInt(),
                    ln.has("unitPrice") ? new BigDecimal(ln.path("unitPrice").asText("0")) : BigDecimal.ZERO));
        }
        try {
            PurchaseOrder po = purchaseOrderService.createAgentDraft(branchId, supplierId, lines);
            BigDecimal total = po.getLines().stream()
                    .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("purchaseOrderId", po.getId());
            out.put("supplierId", supplierId);
            out.put("status", po.getStatus().name());
            out.put("lineCount", po.getLines().size());
            out.put("totalAmount", total);
            return toJson(out);
        } catch (RuntimeException e) {
            return toJson(Map.of("error", e.getMessage()));
        }
    }

    // --- helpers ---------------------------------------------------------------

    private AnthropicApi.Tool tool(String name, String description, Map<String, Object> schema) {
        return new AnthropicApi.Tool(name, description, schema);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schema(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Bad tool schema", e);
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }
}
