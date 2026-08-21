package com.solaria.messenger.dto.response;

import java.time.Instant;

import com.solaria.messenger.model.enums.ObservabilityStepType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Integer tokensIn;
    private Integer tokensOut;
    private Integer tokensTotal;
    private Double latencyMs;
    private Boolean status;
    private String error;
    private Instant timestamp;
}
