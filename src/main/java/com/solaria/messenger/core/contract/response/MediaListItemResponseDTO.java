package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

/** Uma mídia ainda disponível, com a mensagem a que pertence e a descrição do anexo. */
public record MediaListItemResponseDTO(
        String messageId,
        String conversationId,
        UUID senderId,
        Instant timestamp,
        AttachmentResponseDTO attachment) {
}
