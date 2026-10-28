package com.solaria.messenger.core.contract.response;

import java.util.List;

public record AdminConversationPageDTO(
        List<AdminConversationSummaryDTO> items,
        int total,
        boolean truncated) {
}
