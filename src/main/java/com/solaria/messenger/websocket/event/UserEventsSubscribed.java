package com.solaria.messenger.websocket.event;

import java.util.UUID;

/** Emitido somente depois que o broker registrou o SUBSCRIBE do usuário no próprio tópico {@code events}. */
public record UserEventsSubscribed(String sessionId, String subscriptionId, UUID userId) { }
