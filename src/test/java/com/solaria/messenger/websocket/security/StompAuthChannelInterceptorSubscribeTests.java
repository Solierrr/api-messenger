package com.solaria.messenger.websocket.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.websocket.event.UserEventsSubscribed;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/**
 * Cobre o disparo do retrato de presença: só o SUBSCRIBE do próprio {@code events}, já aceito pelo broker, emite
 * {@link UserEventsSubscribed}.
 */
class StompAuthChannelInterceptorSubscribeTests {

    private static final UUID USER = UUID.fromString("7f8e9a10-1b2c-4d3e-8f40-5a6b7c8d9e0f");

    private ApplicationEventPublisher events;
    private LocalWebSocketSessionRegistry sessions;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        events = mock(ApplicationEventPublisher.class);
        sessions = mock(LocalWebSocketSessionRegistry.class);
        given(sessions.isOperational("s1")).willReturn(true);
        interceptor = new StompAuthChannelInterceptor(mock(JwtDecoder.class), mock(StompDestinationAuthorizer.class),
                events, mock(UserAccessHelper.class), sessions);
    }

    @Test
    void subscribingToTheOwnEventsTopicEmitsTheEvent() {
        handled(StompDestinations.userTopic(USER, "events"), "sub-1", null);

        verify(events).publishEvent(new UserEventsSubscribed("s1", "sub-1", USER));
    }

    @Test
    void otherTopicsAndOtherUsersEmitNothing() {
        handled(StompDestinations.userTopic(USER, "acks"), "sub-1", null);
        handled(StompDestinations.userTopic(UUID.randomUUID(), "events"), "sub-2", null);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void subscriptionRefusedByTheBrokerEmitsNothing() {
        handled(StompDestinations.userTopic(USER, "events"), "sub-1", new IllegalStateException("broker"));

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void subscriptionWithoutIdOrOnRevokedSessionEmitsNothing() {
        handled(StompDestinations.userTopic(USER, "events"), " ", null);
        given(sessions.isOperational("s1")).willReturn(false);
        handled(StompDestinations.userTopic(USER, "events"), "sub-3", null);

        verify(events, never()).publishEvent(any(Object.class));
    }

    private void handled(String destination, String subscriptionId, Exception failure) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("s1");
        accessor.setDestination(destination);
        accessor.setSubscriptionId(subscriptionId);
        Jwt jwt = mock(Jwt.class);
        given(jwt.getSubject()).willReturn(USER.toString());
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("jwt", jwt);
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.afterMessageHandled(message, mock(MessageChannel.class), mock(AbstractBrokerMessageHandler.class), failure);
    }
}
