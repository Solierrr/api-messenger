package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

/** Resumo de uma conversa com o chatbot no histórico administrativo */
public record AdminConversationSummaryDTO(
        String sessionId,
        UUID userId,
        String assistant,
        String source,
        Instant startedAt,
        int messageCount,
        String firstMessage,
        Integer nps,
        double costUsd,
        double latencyMs,
        boolean error) {
}
