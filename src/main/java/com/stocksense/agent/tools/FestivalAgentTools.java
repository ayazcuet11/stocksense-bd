package com.stocksense.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.agent.anthropic.AnthropicApi;
import com.stocksense.domain.BranchStock;
import com.stocksense.domain.FestivalEvent;
import com.stocksense.domain.Product;
import com.stocksense.domain.SaleLine;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.BranchStockRepository;
import com.stocksense.repository.FestivalEventRepository;
import com.stocksense.repository.ProductRepository;
import com.stocksense.repository.SaleLineRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The function-calling tools the Festival Demand Agent can invoke. Every tool is tenant-scoped via
 * {@link TenantContext} and validates that the branch/product belongs to the caller's tenant — tool
 * inputs from the model are treated as strictly as any external input.
 */
@Component
public class FestivalAgentTools {

    static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    public static final String GET_SALES_HISTORY = "getSalesHistory";
    public static final String GET_UPCOMING_FESTIVALS = "getUpcomingFestivals";
    public static final String GET_CURRENT_STOCK = "getCurrentStock";
    public static final String SUBMIT_FORECAST = "submitFestivalForecast";

    private final SaleLineRepository saleLineRepository;
    private final FestivalEventRepository festivalEventRepository;
    private final BranchStockRepository branchStockRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    // The agent layer only serializes plain values (no java.time), so a vanilla mapper is enough and
    // keeps us off the shared HTTP ObjectMapper. Spring Boot 4 here doesn't expose one for injection.
    private final ObjectMapper mapper = new ObjectMapper();

    public FestivalAgentTools(SaleLineRepository saleLineRepository,
                              FestivalEventRepository festivalEventRepository,
                              BranchStockRepository branchStockRepository,
                              BranchRepository branchRepository,
                              ProductRepository productRepository) {
        this.saleLineRepository = saleLineRepository;
        this.festivalEventRepository = festivalEventRepository;
        this.branchStockRepository = branchStockRepository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
    }

    /** Tool schemas advertised to the model (the 3 data tools + the structured submit tool). */
    public List<AnthropicApi.Tool> specs() {
        return List.of(
                tool(GET_SALES_HISTORY,
                        "Total units sold and average daily sales for a product at a branch over a date "
                                + "range. Use last year's window around a festival to estimate the surge.",
                        schema("""
                                {"type":"object","properties":{
                                  "productId":{"type":"integer","description":"Product id"},
                                  "branchId":{"type":"integer","description":"Branch id"},
                                  "fromDate":{"type":"string","description":"Inclusive start date, yyyy-MM-dd"},
                                  "toDate":{"type":"string","description":"Inclusive end date, yyyy-MM-dd"}},
                                  "required":["productId","branchId","fromDate","toDate"]}""")),
                tool(GET_UPCOMING_FESTIVALS,
                        "Festivals and seasonal events occurring within the next N days, with dates.",
                        schema("""
                                {"type":"object","properties":{
                                  "withinDays":{"type":"integer","description":"Horizon in days"}},
                                  "required":["withinDays"]}""")),
                tool(GET_CURRENT_STOCK,
                        "Current on-hand quantity and reorder threshold for a product at a branch.",
                        schema("""
                                {"type":"object","properties":{
                                  "productId":{"type":"integer"},
                                  "branchId":{"type":"integer"}},
                                  "required":["productId","branchId"]}""")),
                tool(SUBMIT_FORECAST,
                        "Submit the final festival demand forecast. Call this exactly once when analysis "
                                + "is complete. Only include products with a real demand signal.",
                        schema("""
                                {"type":"object","properties":{
                                  "summary":{"type":"string","description":"2-3 sentence executive summary"},
                                  "recommendations":{"type":"array","items":{"type":"object","properties":{
                                    "productId":{"type":"integer"},
                                    "festivalName":{"type":"string"},
                                    "festivalDate":{"type":"string","description":"yyyy-MM-dd"},
                                    "currentStock":{"type":"integer"},
                                    "baselineDailyUnits":{"type":"number"},
                                    "expectedDailyUnits":{"type":"number"},
                                    "recommendedStockUpUnits":{"type":"integer"},
                                    "upliftPct":{"type":"number","description":"Expected % increase vs baseline"},
                                    "reasoning":{"type":"string"}},
                                    "required":["productId","festivalName","recommendedStockUpUnits","upliftPct","reasoning"]}}},
                                  "required":["summary","recommendations"]}"""))
        );
    }

    /** Execute a data tool and return a JSON string for the model. */
    public String dispatch(String name, JsonNode input) {
        return switch (name) {
            case GET_SALES_HISTORY -> getSalesHistory(
                    input.path("productId").asLong(),
                    input.path("branchId").asLong(),
                    input.path("fromDate").asText(),
                    input.path("toDate").asText());
            case GET_UPCOMING_FESTIVALS -> getUpcomingFestivals(input.path("withinDays").asInt(90));
            case GET_CURRENT_STOCK -> getCurrentStock(
                    input.path("productId").asLong(),
                    input.path("branchId").asLong());
            default -> toJson(Map.of("error", "Unknown tool: " + name));
        };
    }

    // --- tools -----------------------------------------------------------------

    String getSalesHistory(long productId, long branchId, String fromDate, String toDate) {
        assertBranch(branchId);
        assertProduct(productId);
        Instant from, to;
        try {
            from = LocalDate.parse(fromDate).atStartOfDay(DHAKA).toInstant();
            to = LocalDate.parse(toDate).plusDays(1).atStartOfDay(DHAKA).toInstant();
        } catch (Exception e) {
            return toJson(Map.of("error", "Dates must be yyyy-MM-dd"));
        }
        List<SaleLine> lines = saleLineRepository.findByBranchProductAndDateRange(branchId, productId, from, to);
        long totalUnits = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (SaleLine l : lines) {
            totalUnits += l.getQuantity();
            revenue = revenue.add(l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())));
        }
        long days = Math.max(1, java.time.Duration.between(from, to).toDays());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", productId);
        out.put("branchId", branchId);
        out.put("fromDate", fromDate);
        out.put("toDate", toDate);
        out.put("days", days);
        out.put("totalUnitsSold", totalUnits);
        out.put("totalRevenue", revenue);
        out.put("avgDailyUnits", round((double) totalUnits / days));
        out.put("saleLineCount", lines.size());
        return toJson(out);
    }

    String getUpcomingFestivals(int withinDays) {
        LocalDate today = LocalDate.now(DHAKA);
        List<FestivalEvent> events = festivalEventRepository.findUpcoming(today, today.plusDays(withinDays));
        List<Map<String, Object>> list = events.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getName());
            m.put("type", f.getType());
            m.put("date", f.getGregorianDate().toString());
            m.put("daysAway", java.time.temporal.ChronoUnit.DAYS.between(today, f.getGregorianDate()));
            return m;
        }).toList();
        return toJson(Map.of("today", today.toString(), "withinDays", withinDays, "festivals", list));
    }

    String getCurrentStock(long productId, long branchId) {
        assertBranch(branchId);
        assertProduct(productId);
        BranchStock stock = branchStockRepository.findByBranchIdAndProductId(branchId, productId).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("productId", productId);
        out.put("branchId", branchId);
        out.put("quantity", stock == null ? 0 : stock.getQuantity());
        out.put("reorderThreshold", stock == null ? 0 : stock.getReorderThreshold());
        return toJson(out);
    }

    // --- helpers ---------------------------------------------------------------

    private void assertBranch(long branchId) {
        if (!branchRepository.existsByIdAndTenantId(branchId, TenantContext.get())) {
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        }
    }

    private Product assertProduct(long productId) {
        return productRepository.findByIdAndTenantId(productId, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

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

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
