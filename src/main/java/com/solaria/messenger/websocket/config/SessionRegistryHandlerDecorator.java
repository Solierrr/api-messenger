package com.solaria.messenger.websocket.config;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/**
 * Registra o transporte físico antes do STOMP e garante o cleanup local quando a conexão fecha ou falha.
 *
 * <p><b>Depende de:</b></p>
 * <ul>
 *     <li>{@link LocalWebSocketSessionRegistry} -> guarda as sessões locais e calcula a presença</li>
 * </ul>
 */
class SessionRegistryHandlerDecorator extends WebSocketHandlerDecorator {

    private final LocalWebSocketSessionRegistry sessions;

    SessionRegistryHandlerDecorator(WebSocketHandler delegate, LocalWebSocketSessionRegistry sessions) {
        super(delegate);
        this.sessions = sessions;
    }

    /** Registra o transporte antes do STOMP e o recusa se o handler falhar ao abrir. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // cria sessão de transporte antes de qualquer coisa
        sessions.capture(session);
        try {
            super.afterConnectionEstablished(session);
        } catch (Exception failure) {
            sessions.reject(session.getId());
            throw failure;
        }
    }

    /** Remove a sessão do registro mesmo quando o fechamento do handler falha. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // independente de qualquer exceção, fecha a conexão quando ela é fechada
        try {
            super.afterConnectionClosed(session, status);
        } finally {
            sessions.disconnect(session.getId());
        }
    }
}
