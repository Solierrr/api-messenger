package com.solaria.messenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.Message;
import com.solaria.messenger.repository.MessageRepository;

@ExtendWith(MockitoExtension.class)
class MessageServiceTests {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationService conversationService;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendsMessageAndUpdatesConversationInteraction() {
        Conversation conversation = new Conversation("conversation-1", 1L, "session-1", "active", null, null, null);
        Message message = new Message(null, "conversation-1", "user", "Hello", null);
        given(conversationService.getConversationById("conversation-1")).willReturn(conversation);
        given(messageRepository.save(message)).willReturn(message);

        Message savedMessage = messageService.sendMessage(message);

        ArgumentCaptor<Instant> timestamp = ArgumentCaptor.forClass(Instant.class);
        assertThat(savedMessage).isSameAs(message);
        assertThat(savedMessage.getTimestamp()).isNotNull();
        verify(messageRepository).save(message);
        verify(conversationService).updateLastInteraction(eq(conversation), timestamp.capture());
        assertThat(timestamp.getValue()).isEqualTo(savedMessage.getTimestamp());
    }

    @Test
    void doesNotPersistMessageWhenConversationDoesNotExist() {
        Message message = new Message(null, "missing", "user", "Hello", null);
        given(conversationService.getConversationById("missing"))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        assertThatThrownBy(() -> messageService.sendMessage(message))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void getsMessagesByConversationIdWithPagination() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Message> messages = new PageImpl<>(List.of(new Message("message-1", "conversation-1", "user", "Hello", Instant.now())));
        given(messageRepository.findByConversationIdOrderByTimestampAsc("conversation-1", pageable)).willReturn(messages);

        Page<Message> foundMessages = messageService.getMessagesByConversationId("conversation-1", pageable);

        assertThat(foundMessages).isSameAs(messages);
        verify(messageRepository).findByConversationIdOrderByTimestampAsc("conversation-1", pageable);
    }
}
