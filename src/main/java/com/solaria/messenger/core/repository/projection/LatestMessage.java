package com.solaria.messenger.core.repository.projection;

import java.time.Instant;
import java.util.UUID;

/** Mensagem mais recente usada na projeção de conversas */
public record LatestMessage(String id, int sequence, UUID senderId, String content,
        boolean edited, boolean deleted, Instant timestamp) { }
