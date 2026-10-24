package com.solaria.messenger.websocket.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/** Compartilha o cleanup do registro local entre o DISCONNECT STOMP e o fechamento do transporte. */
@Component
public class WebSocketSessionEventListener {

    private final LocalWebSocketSessionRegistry sessions;

    public WebSocketSessionEventListener(LocalWebSocketSessionRegistry sessions) {
        this.sessions = sessions;
    }

    /** O registro torna chamadas duplicadas de disconnect inofensivas. */
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        sessions.disconnect(event.getSessionId());
    }
}
