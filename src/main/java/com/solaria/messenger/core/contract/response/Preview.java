package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.solaria.messenger.core.model.enums.AttachmentType;

/**
 * Prévia de mensagem com tombstone e nulos explícitos, compartilhada por REST e eventos. {@code attachmentType} diz o
 * tipo do anexo, que a lista usa no lugar do texto quando a mensagem não tem legenda.
 */
public record Preview(
        String id,
        int sequence,
        UUID senderId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String content,
        boolean edited,
        boolean deleted,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) AttachmentType attachmentType) {
}
