package com.stocksense.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "agent_decisions")
public class AgentDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String agentType;

    @Column(columnDefinition = "TEXT")
    private String inputSummary;

    @Column(columnDefinition = "TEXT")
    private String outputSummary;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Boolean approved;
}
