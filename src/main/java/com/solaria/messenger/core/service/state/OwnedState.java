package com.solaria.messenger.core.service.state;

import java.util.UUID;

/** Estado pessoal validado de um participante, com baseline original de admissão. */
public record OwnedState(String conversationId, UUID userId, int baselineSequence, int lastReadSequence,
        boolean muted, boolean archived) {
}
