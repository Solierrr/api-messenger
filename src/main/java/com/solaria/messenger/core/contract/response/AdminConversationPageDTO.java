package com.solaria.messenger.core.contract.response;

import java.util.List;

/** Página do histórico administrativo; {@code truncated} indica que a busca passou do limite de varredura */
public record AdminConversationPageDTO(
        List<AdminConversationSummaryDTO> items,
        int total,
        boolean truncated) {
}
