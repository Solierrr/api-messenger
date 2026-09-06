package com.solaria.messenger.dto.request;

import java.time.Instant;

import com.solaria.messenger.model.enums.ObservabilityStepType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LlmObservabilityRequestDTO {

    @NotBlank(message = "node é obrigatório")
    private String node;

    @NotNull(message = "stepOrder é obrigatório")
    @PositiveOrZero(message = "stepOrder não pode ser negativo")
    private Integer stepOrder;

    @NotNull(message = "stepType é obrigatório")
    private ObservabilityStepType stepType;

    @NotBlank(message = "model é obrigatório")
    private String model;

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    @NotNull(message = "tokensIn é obrigatório")
    @PositiveOrZero(message = "tokensIn não pode ser negativo")
    private Integer tokensIn;

    @NotNull(message = "tokensOut é obrigatório")
    @PositiveOrZero(message = "tokensOut não pode ser negativo")
    private Integer tokensOut;

    @NotNull(message = "tokensTotal é obrigatório")
    @PositiveOrZero(message = "tokensTotal não pode ser negativo")
    private Integer tokensTotal;

    @NotNull(message = "latencyMs é obrigatório")
    @PositiveOrZero(message = "latencyMs não pode ser negativo")
    private Double latencyMs;

    @NotBlank(message = "status é obrigatório")
    private String status;

    private String error;

    private Instant timestamp;
}
