package com.solaria.messenger.core.contract.response;

import java.time.Instant;

import com.solaria.messenger.core.model.enums.Environment;
import com.solaria.messenger.core.model.enums.ObservabilityStepType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com um evento de telemetria de passo do pipeline de LLM.
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmObservabilityResponseDTO {

    private String id;
    private String node;
    private Integer stepOrder;
    private ObservabilityStepType stepType;
    private String model;
    private String conversationId;
    private Environment environment;
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer tokensTotal;
    private Double latencyMs;
    private String status;
    private String error;
    private Instant timestamp;
    private Double costUsd;
}
