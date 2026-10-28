package com.solaria.messenger.websocket.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.websocket.event.StompFrameDenied;

/**
 * Cobre os destinos STOMP do broker: os tópicos que os publicadores montam são os que a autorização reconhece,
 * curingas e '/' são recusados, e o SUBSCRIBE no tópico privado só vale para o próprio user.
 */
class StompDestinationsTests {

    private static final UUID USER = UUID.fromString("7f8e9a10-1b2c-4d3e-8f40-5a6b7c8d9e0f");

    @Test
    void parsesTheTopicsThePublishersBuild() {
        assertEquals("abc_1-2", StompDestinations.conversationTopicId(StompDestinations.conversationTopic("abc_1-2")));
        assertEquals(USER.toString(), StompDestinations.userTopicOwner(StompDestinations.userTopic(USER, "acks")));
        assertEquals(USER.toString(), StompDestinations.userTopicOwner(StompDestinations.userTopic(USER, "conversations")));
    }

    // '/' é recusado pelo RabbitMQ; '.', '*' e '#' são curingas ou separadores do amq.topic
    @Test
    void rejectsSlashesAndRabbitWildcards() {
        assertNull(StompDestinations.conversationTopicId("/topic/conversations/abc"));
        assertNull(StompDestinations.conversationTopicId("/topic/conversations.*"));
        assertNull(StompDestinations.conversationTopicId("/topic/conversations.#"));
        assertNull(StompDestinations.conversationTopicId("/topic/conversations.a.b"));
        assertNull(StompDestinations.userTopicOwner("/topic/users.*.acks"));
        assertNull(StompDestinations.userTopicOwner("/topic/users.#"));
        assertNull(StompDestinations.userTopicOwner("/topic/users." + USER + ".*"));
        assertNull(StompDestinations.userTopicOwner("/topic/users." + USER + ".other"));
        assertNull(StompDestinations.userTopicOwner("/user/queue/acks"));
    }

    @Test
    void subscribesOnlyToTheOwnUserTopicAndDropsQueueOptions() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        var authorizer = new StompDestinationAuthorizer(mock(ConversationAccessHelper.class), events);

        Message<byte[]> own = subscribe(StompDestinations.userTopic(USER, "errors"));
        Message<byte[]> allowed = authorizer.authorizeSubscribe(own, StompHeaderAccessor.wrap(own), USER.toString());
        assertNotNull(allowed);
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(allowed);
        assertNull(headers.getFirstNativeHeader("durable"));
        assertNull(headers.getFirstNativeHeader("x-queue-name"));

        Message<byte[]> other = subscribe(StompDestinations.userTopic(UUID.randomUUID(), "errors"));
        assertNull(authorizer.authorizeSubscribe(other, StompHeaderAccessor.wrap(other), USER.toString()));
        verify(events).publishEvent(any(StompFrameDenied.class));
    }

    private static Message<byte[]> subscribe(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-1");
        accessor.setNativeHeader("durable", "true");
        accessor.setNativeHeader("x-queue-name", "fila-do-cliente");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
