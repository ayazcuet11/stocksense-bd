package com.stocksense.controller;

import com.stocksense.agent.FestivalForecastAgent;
import com.stocksense.agent.InsightAgent;
import com.stocksense.agent.anthropic.AnthropicClient;
import com.stocksense.agent.job.ReorderJobPublisher;
import com.stocksense.domain.AgentDecision;
import com.stocksense.dto.forecast.FestivalForecastResponse;
import com.stocksense.dto.insight.InsightRequest;
import com.stocksense.dto.insight.InsightResponse;
import com.stocksense.exception.AgentUnavailableException;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.AgentDecisionRepository;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.UserRepository;
import com.stocksense.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agents")
public class AgentController {

    private final FestivalForecastAgent festivalAgent;
    private final InsightAgent insightAgent;
    private final AnthropicClient anthropicClient;
    private final AgentDecisionRepository decisionRepository;
    private final ReorderJobPublisher reorderJobPublisher;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    /** Whether the AI layer is configured — lets the UI show a helpful banner instead of erroring. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map
                .of("enabled", anthropicClient.isEnabled());
    }

    /** Run the Festival Demand Agent for a branch. */
    @GetMapping("/festival-forecast")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public FestivalForecastResponse festivalForecast(@RequestParam Long branchId,
                                                     @RequestParam(defaultValue = "120") int horizonDays) {
        if (horizonDays < 7 || horizonDays > 365)
            throw new IllegalArgumentException("horizonDays must be between 7 and 365");

        return festivalAgent
                .forecast(branchId, horizonDays);
    }

    /**
     * Smart Reorder Agent (Phase 4). Enqueues an async scan that drafts PENDING_APPROVAL purchase
     * orders for low-stock products. Runs on the broker, not the request thread — returns 202.
     */
    @PostMapping("/reorder-scan")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> reorderScan(@RequestParam Long branchId,
                                           @RequestParam(defaultValue = "120") int horizonDays,
                                           Principal principal) {

        if (!anthropicClient.isEnabled())
            throw new AgentUnavailableException(
                    "AI agent is not configured. Set the ANTHROPIC_API_KEY environment variable.");

        if (!branchRepository.existsByIdAndTenantId(branchId, TenantContext.get()))
            throw new ResourceNotFoundException("Branch not found: " + branchId);

        var user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var jobId = reorderJobPublisher.enqueue(branchId, user.getId(), horizonDays);

        return Map.of("jobId", jobId
                , "status", "QUEUED"
                , "branchId", branchId);
    }

    /**
     * Insight Agent (Phase 5). Answers a natural-language question (Bangla or English) by generating
     * and running a guarded, read-only SELECT, then returning the rows plus a plain-language answer.
     * Synchronous — the query is fast — and tenant-scoped via {@link TenantContext}.
     */
    @PostMapping("/insight")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public InsightResponse insight(@Valid @RequestBody InsightRequest request) {
        return insightAgent.ask(request.question());
    }

    /** Audit log of agent decisions (most recent first). */
    @GetMapping("/decisions")
    public List<AgentDecision> decisions(@RequestParam(defaultValue = "FESTIVAL_DEMAND") String agentType) {

        return decisionRepository
                .findAllByAgentTypeOrderByCreatedAtDesc(agentType);
    }
}
