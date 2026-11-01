package com.solaria.messenger.websocket.session;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.port.UserPresence;

/** Presença online do user a partir das sessões WebSocket admitidas em qualquer pod. */
@Component
public class WebSocketUserPresence implements UserPresence {

    private final LocalWebSocketSessionRegistry sessions;

    public WebSocketUserPresence(LocalWebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean isOnline(UUID userId) {
        return sessions.isOnlineAnywhere(userId);
    }
}
