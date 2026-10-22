package com.solaria.messenger.websocket.event;

import java.util.UUID;

import com.solaria.messenger.websocket.dto.response.WsErrorPayload;

/** Feedback privado do interceptor, publicado sem acoplar a configuração STOMP ao broker. */
public record StompFrameDenied(UUID userId, WsErrorPayload error) {
}
