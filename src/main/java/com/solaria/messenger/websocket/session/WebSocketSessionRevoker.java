package com.solaria.messenger.websocket.session;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.service.port.SessionRevoker;

/** Encerra as conexões locais depois que o domínio registra a perda de participação. */
@Component
public class WebSocketSessionRevoker implements SessionRevoker {

    private final LocalWebSocketSessionRegistry sessions;

    public WebSocketSessionRevoker(LocalWebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    /** Revoga também CONNECTs ainda pendentes; a reconexão continua permitida. */
    @Override
    public void revokeUser(UUID userId) {
        sessions.closeUser(userId);
    }
}
