package com.solaria.messenger.websocket.event;

import java.util.UUID;

/** Emitido somente depois que o broker registrou um SUBSCRIBE autorizado no tópico da conversa. */
public record ConversationSubscriptionRegistered(String sessionId, String subscriptionId, UUID userId,
        String conversationId) { }
