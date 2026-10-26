package com.solaria.messenger.websocket.session;

import java.util.UUID;

import org.springframework.web.socket.WebSocketSession;

/**
 * Estado local de uma conexão física. Mutável e sem sincronização própria: só é lido e alterado
 * sob o monitor de {@link LocalWebSocketSessionRegistry}.
 */
final class LocalSession {

    final WebSocketSession transport;
    UUID userId;
    boolean admitted;
    boolean revoked;
    // o cliente já mandou DISCONNECT; a sessão ainda existe até o socket fechar
    boolean disconnecting;

    LocalSession(WebSocketSession transport) {
        this.transport = transport;
    }
}
