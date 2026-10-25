package com.solaria.messenger.websocket.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.solaria.messenger.websocket.event.StompFrameDenied;
import com.solaria.messenger.websocket.error.StompErrorHandler;

/** Entrega ao usuário as recusas de frame feitas pelos interceptores, que não alcançam o {@link StompErrorHandler} */
@Component
public class StompFrameDeniedListener {

    private static final Logger log = LoggerFactory.getLogger(StompFrameDeniedListener.class);
    private final SimpMessagingTemplate messaging;

    public StompFrameDeniedListener(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Erros de autorização descartam o comando e avisam o usuário, sem encerrar outras sessões */
    @EventListener
    public void onFrameDenied(StompFrameDenied denied) {
        try {
            messaging.convertAndSendToUser(denied.userId().toString(), "/queue/errors", denied.error());
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar erro STOMP (userId={})", denied.userId(), failure);
        }
    }
}
