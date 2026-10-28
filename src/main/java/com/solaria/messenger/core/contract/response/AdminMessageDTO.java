package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.List;

public record AdminMessageDTO(
        String role,
        String content,
        Instant timestamp,
        Integer turn,
        Integer threadTurn,
        String turnId,
        List<String> specialistsUsed,
        List<String> workflowSteps,
        double latencyMs,
        double costUsd,
        List<LlmObservabilityResponseDTO> steps) {
}
