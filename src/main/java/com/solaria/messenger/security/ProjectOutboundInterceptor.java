package com.solaria.messenger.security;

import com.solaria.messenger.service.*;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectOutboundInterceptor implements ChannelInterceptor {
    private final ConversationService conversations;
    private final SessionRegistryService sessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var h = SimpMessageHeaderAccessor.wrap(message);
        var destination = h.getDestination();
        if (destination == null || !destination.startsWith("/topic/conversations/")) return message;
        String user = sessions.userForSession(h.getSessionId());
        if (user == null) return null;
        try {
            String id = destination.substring("/topic/conversations/".length());
            return conversations.canReadFor(
                            conversations.requireEntityById(id), UUID.fromString(user))
                    ? message
                    : null;
        } catch (Exception failure) {
            return null;
        }
    }
}
