package com.solaria.messenger.core.contract.response;

import java.util.List;
import java.util.Map;

/** Detalhe de uma conversa com o chatbot no histórico administrativo: resumo, thread, detalhes e mensagens */
public record AdminConversationDetailDTO(
        AdminConversationSummaryDTO summary,
        String threadId,
        Map<String, Object> userDetails,
        List<AdminMessageDTO> messages) {
}
