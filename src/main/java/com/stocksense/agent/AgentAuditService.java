package com.stocksense.agent;

import com.stocksense.domain.AgentDecision;
import com.stocksense.repository.AgentDecisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an immutable audit record for every agent action. Per the brief, every agent output is
 * persisted to {@code agent_decisions} (input, output, reasoning) before it is acted on.
 */
@Service
public class AgentAuditService {

    private final AgentDecisionRepository repository;

    public AgentAuditService(AgentDecisionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AgentDecision record(String agentType, String inputSummary, String outputSummary,
                                String reasoning, Boolean approved) {
        AgentDecision decision = new AgentDecision();
        decision.setAgentType(agentType);
        decision.setInputSummary(inputSummary);
        decision.setOutputSummary(outputSummary);
        decision.setReasoning(reasoning);
        decision.setApproved(approved);
        return repository.save(decision);
    }
}
