package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Prévia de mensagem com tombstone e nulos explícitos, compartilhada por REST e eventos. */
public record Preview(
        String id,
        int sequence,
        UUID senderId,
        @JsonInclude(JsonInclude.Include.ALWAYS) String content,
        boolean edited,
        boolean deleted,
        Instant timestamp) {
}
