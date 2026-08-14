package com.solaria.messenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.repository.ConversationRepository;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTests {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createsConversationWithActiveStatusAndTimestamps() {
        Conversation conversation = conversation();
        conversation.setStatus(null);
        given(conversationRepository.save(conversation)).willReturn(conversation);

        Conversation savedConversation = conversationService.createConversation(conversation);

        assertThat(savedConversation.getStatus()).isEqualTo("active");
        assertThat(savedConversation.getStartedAt()).isNotNull();
        assertThat(savedConversation.getLastInteractionAt()).isEqualTo(savedConversation.getStartedAt());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void getsConversationById() {
        Conversation conversation = conversation();
        given(conversationRepository.findById("conversation-1")).willReturn(Optional.of(conversation));

        Conversation foundConversation = conversationService.getConversationById("conversation-1");

        assertThat(foundConversation).isSameAs(conversation);
    }

    @Test
    void returnsNotFoundWhenConversationDoesNotExist() {
        given(conversationRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getConversationById("missing"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updatesLastInteraction() {
        Conversation conversation = conversation();
        Instant timestamp = Instant.parse("2026-08-13T22:00:00Z");

        conversationService.updateLastInteraction(conversation, timestamp);

        assertThat(conversation.getLastInteractionAt()).isEqualTo(timestamp);
        verify(conversationRepository).save(conversation);
    }

    @Test
    void getsConversationsByUserId() {
        List<Conversation> conversations = List.of(conversation());
        given(conversationRepository.findByUserId(1L)).willReturn(conversations);

        List<Conversation> foundConversations = conversationService.getConversationsByUserId(1L);

        assertThat(foundConversations).isSameAs(conversations);
    }

    private Conversation conversation() {
        return new Conversation("conversation-1", 1L, "session-1", "active", null, null, null);
    }
}
