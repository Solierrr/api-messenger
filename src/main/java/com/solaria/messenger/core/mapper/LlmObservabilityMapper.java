package com.solaria.messenger.core.mapper;

import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.model.LlmObservability;

/** Mapeamento único da entidade persistida para o contrato público, sem consultas ou autorização. */
public final class LlmObservabilityMapper {
    private LlmObservabilityMapper() { }

    /** Converte a entity para DTO */
    public static LlmObservabilityResponseDTO toResponse(LlmObservability observability) {
        return LlmObservabilityResponseDTO.builder()
                .id(observability.getId())
                .node(observability.getNode())
                .stepOrder(observability.getStepOrder())
                .stepType(observability.getStepType())
                .model(observability.getModel())
                .conversationId(observability.getConversationId())
                .environment(observability.getEnvironment())
                .tokensIn(observability.getTokensIn())
                .tokensOut(observability.getTokensOut())
                .tokensTotal(observability.getTokensTotal())
                .latencyMs(observability.getLatencyMs())
                .status(observability.getStatus())
                .error(observability.getError())
                .timestamp(observability.getTimestamp())
                .costUsd(observability.getCostUsd())
                .build();
    }
}
