package com.solaria.messenger.websocket.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompBrokerRelayMessageHandler;

/** Sobe o contexto nos dois modos de broker e confere qual handler STOMP foi montado. */
class WebSocketBrokerModeTests {

    @Nested
    @SpringBootTest(properties = "app.broker.enabled=false")
    class SimpleBroker {

        @Autowired
        ApplicationContext context;

        @Test
        void usesTheInMemoryBrokerWithHeartbeats() {
            Map<String, SimpleBrokerMessageHandler> simple = context.getBeansOfType(SimpleBrokerMessageHandler.class);
            assertEquals(1, simple.size());
            assertTrue(context.getBeansOfType(StompBrokerRelayMessageHandler.class).isEmpty());

            SimpleBrokerMessageHandler handler = simple.values().iterator().next();
            assertArrayEquals(new long[] { 10_000, 10_000 }, handler.getHeartbeatValue());
            assertNotNull(handler.getTaskScheduler());
            assertTrue(handler.getDestinationPrefixes().contains("/topic"));
        }
    }

    @Nested
    @SpringBootTest(properties = { "app.broker.enabled=true", "app.broker.host=broker" })
    class Relay {

        @Autowired
        ApplicationContext context;

        @Test
        void usesTheStompRelay() {
            assertEquals(1, context.getBeansOfType(StompBrokerRelayMessageHandler.class).size());
            assertTrue(context.getBeansOfType(SimpleBrokerMessageHandler.class).isEmpty());
        }
    }
}
