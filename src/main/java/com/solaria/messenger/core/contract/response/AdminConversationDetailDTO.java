package com.solaria.messenger.core.contract.response;

import java.util.List;
import java.util.Map;

public record AdminConversationDetailDTO(
        AdminConversationSummaryDTO summary,
        String threadId,
        Map<String, Object> userDetails,
        List<AdminMessageDTO> messages) {
}
