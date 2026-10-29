package com.solaria.messenger.websocket.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.security.StompDestinations;

/** Conecta por WebSocket real com o relay desligado e confere que o broker em memória entrega o evento ao assinante. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.broker.enabled=false")
class SimpleBrokerDeliveryTests {

    private static final UUID USER = UUID.fromString("7f8e9a10-1b2c-4d3e-8f40-5a6b7c8d9e0f");

    @LocalServerPort
    int port;

    @Autowired
    SimpMessagingTemplate messaging;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    UserAccessService userAccess;

    @Test
    void deliversTopicEventsToTheSubscribedClient() throws Exception {
        when(jwtDecoder.decode(any())).thenReturn(Jwt.withTokenValue("token").header("alg", "none")
                .subject(USER.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter() {
            {
                addSupportedMimeTypes(MimeTypeUtils.APPLICATION_JSON);
            }
        });

        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer token");
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        StompSession session = client.connectAsync("ws://localhost:" + port + "/messaging/ws",
                new WebSocketHttpHeaders(), connect, new StompSessionHandlerAdapter() { })
                .get(15, TimeUnit.SECONDS);
        try {
            String destination = StompDestinations.userTopic(USER, "acks");
            session.subscribe(destination, new StompSessionHandlerAdapter() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.add((String) payload);
                }
            });

            String delivered = null;
            for (int attempt = 0; attempt < 20 && delivered == null; attempt++) {
                messaging.convertAndSend(destination, WsEventEnvelope.builder().type("MESSAGE_ACK").build());
                delivered = received.poll(500, TimeUnit.MILLISECONDS);
            }
            assertNotNull(delivered);
            assertTrue(delivered.contains("MESSAGE_ACK"));
        } finally {
            session.disconnect();
            client.stop();
        }
    }
}
