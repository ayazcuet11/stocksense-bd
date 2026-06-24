package com.stocksense.repository;

import com.stocksense.domain.AgentDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentDecisionRepository extends JpaRepository<AgentDecision, Long> {
    List<AgentDecision> findAllByAgentTypeOrderByCreatedAtDesc(String agentType);
    Optional<AgentDecision> findFirstByAgentTypeOrderByCreatedAtDesc(String agentType);
}
