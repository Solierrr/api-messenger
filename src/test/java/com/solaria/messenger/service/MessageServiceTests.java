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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.exception.InvalidFieldException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.Message;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.MessageType;
import com.solaria.messenger.repository.MessageRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@ExtendWith(MockitoExtension.class)
class MessageServiceTests {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private RbacAuthorizationService rbac;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendsUserMessageAndUpdatesConversationInteraction() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation();
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);

        given(conversationService.requireEntityById("conversation-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.save(any(Message.class))).willAnswer(invocation -> invocation.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        ArgumentCaptor<Instant> timestamp = ArgumentCaptor.forClass(Instant.class);
        assertThat(response.getSenderId()).isEqualTo(senderId);
        assertThat(response.getConversationId()).isEqualTo("conversation-1");
        assertThat(response.getContent()).isEqualTo("Hello");
        assertThat(response.getTimestamp()).isNotNull();
        verify(conversationService).requireParticipant(conversation);
        verify(conversationService).updateLastInteraction(eq(conversation), timestamp.capture());
        assertThat(timestamp.getValue()).isEqualTo(response.getTimestamp());
    }

    @Test
    void rejectsChatbotToUserMessageType() {
        MessageRequestDTO dto = messageRequest(MessageType.CHATBOT_TO_USER);

        assertThatThrownBy(() -> messageService.sendUserMessage(dto))
                .isInstanceOf(InvalidFieldException.class);

        verifyNoInteractions(messageRepository, conversationService);
    }

    @Test
    void doesNotPersistMessageWhenConversationDoesNotExist() {
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setConversationId("missing");

        given(conversationService.requireEntityById("missing"))
                .willThrow(new ResourceNotFoundException("Conversa não encontrada com id: missing"));

        assertThatThrownBy(() -> messageService.sendUserMessage(dto))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void getsMessagesByConversationId() {
        Conversation conversation = conversation();
        List<Message> messages = List.of(message());
        given(conversationService.requireEntityById("conversation-1")).willReturn(conversation);
        given(messageRepository.findByConversationIdOrderByTimestampAsc("conversation-1")).willReturn(messages);

        List<MessageResponseDTO> foundMessages = messageService.getMessagesByConversationId("conversation-1");

        assertThat(foundMessages).hasSize(1);
        assertThat(foundMessages.get(0).getId()).isEqualTo(messages.get(0).getId());
        verify(conversationService).requireParticipant(conversation);
    }

    private MessageRequestDTO messageRequest(MessageType messageType) {
        MessageRequestDTO dto = new MessageRequestDTO();
        dto.setConversationId("conversation-1");
        dto.setMessageType(messageType);
        dto.setRole("user");
        dto.setContent("Hello");
        return dto;
    }

    private Conversation conversation() {
        return new Conversation("conversation-1", UUID.randomUUID(), UUID.randomUUID(),
                ConversationType.USER_CONVERSATION, null, null, ConversationStatus.ACTIVE,
                Instant.now(), Instant.now());
    }

    private Message message() {
        return new Message("message-1", "conversation-1", UUID.randomUUID(), "user",
                MessageType.USER_TO_USER, "Hello", null, Instant.now());
    }
}
