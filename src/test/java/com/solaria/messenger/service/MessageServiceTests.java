package com.solaria.messenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;

import com.solaria.messenger.dto.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.exception.BusinessRuleException;
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

    @Mock
    private MessageBroadcastService messageBroadcastService;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendsUserMessageOnDirectConversationAndUpdatesInteraction() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(conversationService.nextSequence("c-1")).willReturn(7);
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getSenderId()).isEqualTo(senderId);
        assertThat(response.getConversationId()).isEqualTo("c-1");
        assertThat(response.getContent()).isEqualTo("Olá");
        assertThat(response.getSequence()).isEqualTo(7);

        verify(conversationService).requireParticipant(conversation);
        verify(conversationService).requireActive(conversation);

        ArgumentCaptor<Instant> ts = ArgumentCaptor.forClass(Instant.class);
        verify(conversationService).updateLastInteraction(eq(conversation), ts.capture());
        assertThat(ts.getValue()).isEqualTo(response.getTimestamp());
        InOrder order = inOrder(messageRepository, messageBroadcastService);
        order.verify(messageRepository).save(any(Message.class));
        order.verify(messageBroadcastService).broadcastInline(any(Message.class));
    }

    @Test
    void sendsGroupMessageWithUserToGroupType() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.GROUP);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_GROUP);

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getMessageType()).isEqualTo(MessageType.USER_TO_GROUP);
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void rejectsChatbotToUserMessageTypeBeforeTouchingRepositories() {
        MessageRequestDTO dto = messageRequest(MessageType.CHATBOT_TO_USER);

        assertThatThrownBy(() -> messageService.sendUserMessage(dto))
                .isInstanceOf(InvalidFieldException.class);

        verifyNoInteractions(messageRepository, conversationService);
    }

    @Test
    void rejectsMessageTypeThatDoesNotMatchConversationType() {
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_GROUP);

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);

        assertThatThrownBy(() -> messageService.sendUserMessage(dto))
                .isInstanceOfSatisfying(InvalidFieldException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void rejectsMessageWhenConversationIsDeactivated() {
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        doThrow(new BusinessRuleException("A conversa está desativada e não aceita novas mensagens."))
                .when(conversationService).requireActive(conversation);

        assertThatThrownBy(() -> messageService.sendUserMessage(dto))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void doesNotPersistMessageWhenConversationDoesNotExist() {
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setConversationId("missing");

        given(conversationService.requireEntityById("missing"))
                .willThrow(new ResourceNotFoundException("Conversa não encontrada com id: missing"));

        assertThatThrownBy(() -> messageService.sendUserMessage(dto))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void getsMessagesByConversationIdAfterParticipantCheck() {
        Conversation conversation = conversation(ConversationType.GROUP);
        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(messageRepository.findByConversationIdOrderBySequenceAscTimestampAsc(eq("c-1"), any(Limit.class)))
                .willReturn(List.of(message()));

        List<MessageResponseDTO> messages = messageService.getMessagesByConversationId("c-1");

        assertThat(messages).hasSize(1);
        verify(conversationService).requireParticipant(conversation);
    }

    @Test
    void getsOnlyMessagesAfterRequestedSequence() {
        Conversation conversation = conversation(ConversationType.GROUP);
        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(messageRepository.findByConversationIdAndSequenceGreaterThanOrderBySequenceAscTimestampAsc(
                eq("c-1"), eq(5), any(Limit.class))).willReturn(List.of(message()));

        assertThat(messageService.getMessagesByConversationId("c-1", 5)).hasSize(1);
        verify(messageRepository, never())
                .findByConversationIdOrderBySequenceAscTimestampAsc(any(), any());
    }

    @Test
    void defaultsPageSizeTo100WhenLimitNotProvided() {
        Conversation conversation = conversation(ConversationType.GROUP);
        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        ArgumentCaptor<Limit> limitCaptor = ArgumentCaptor.forClass(Limit.class);
        given(messageRepository.findByConversationIdOrderBySequenceAscTimestampAsc(eq("c-1"), limitCaptor.capture()))
                .willReturn(List.of());

        messageService.getMessagesByConversationId("c-1", null, null);

        assertThat(limitCaptor.getValue().max()).isEqualTo(100);
    }

    @Test
    void clampsPageSizeAbove500() {
        Conversation conversation = conversation(ConversationType.GROUP);
        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        ArgumentCaptor<Limit> limitCaptor = ArgumentCaptor.forClass(Limit.class);
        given(messageRepository.findByConversationIdOrderBySequenceAscTimestampAsc(eq("c-1"), limitCaptor.capture()))
                .willReturn(List.of());

        messageService.getMessagesByConversationId("c-1", null, 10_000);

        assertThat(limitCaptor.getValue().max()).isEqualTo(500);
    }

    // ------------------------------------------------------------------------- F-11 (idempotência)

    @Test
    void duplicateClientMessageIdFromSameSenderReturnsStoredMessageWithoutPublishingAgain() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setClientMessageId("client-abc");
        Message stored = message();
        stored.setSenderId(senderId);
        stored.setClientMessageId("client-abc");

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.findByConversationIdAndSenderIdAndClientMessageId("c-1", senderId, "client-abc"))
                .willReturn(Optional.of(stored));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getClientMessageId()).isEqualTo("client-abc");
        verify(messageRepository, never()).save(any(Message.class));
        verifyNoInteractions(messageBroadcastService);
    }

    @Test
    void sameClientMessageIdFromDifferentSenderCreatesNewMessage() {
        // Simula a colisao: OUTRO remetente ja tem uma mensagem com este clientMessageId nesta
        // conversa, mas o lookup e escopado por (conversa, REMETENTE ATUAL, clientMessageId) - a
        // ausencia de resultado para o remetente atual deve criar uma mensagem nova, nunca
        // devolver a de outra pessoa.
        UUID currentSender = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.GROUP);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_GROUP);
        dto.setClientMessageId("shared-id");

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(currentSender);
        given(messageRepository.findByConversationIdAndSenderIdAndClientMessageId("c-1", currentSender, "shared-id"))
                .willReturn(Optional.empty());
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getSenderId()).isEqualTo(currentSender);
        assertThat(response.getClientMessageId()).isEqualTo("shared-id");
        verify(messageRepository).save(any(Message.class));
        verify(messageBroadcastService).broadcastInline(any(Message.class));
    }

    @Test
    void blankClientMessageIdIsStoredAsNull() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setClientMessageId("   ");

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getClientMessageId()).isNull();
        verify(messageRepository, never())
                .findByConversationIdAndSenderIdAndClientMessageId(any(), any(), any());
    }

    @Test
    void duplicateKeyOnSaveReturnsExistingMessageWithoutInteractionUpdateOrBroadcast() {
        // Corrida: 2 requisicoes com o mesmo (conversa, remetente, clientMessageId) passam pelo
        // lookup antes de qualquer uma salvar (ambas veem Optional.empty()); a que perde a
        // corrida do indice unico recebe DuplicateKeyException do Mongo no save.
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setClientMessageId("racing-id");
        Message winner = message();
        winner.setSenderId(senderId);
        winner.setClientMessageId("racing-id");

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.findByConversationIdAndSenderIdAndClientMessageId("c-1", senderId, "racing-id"))
                .willReturn(Optional.empty(), Optional.of(winner));
        given(messageRepository.save(any(Message.class))).willThrow(new DuplicateKeyException("E11000"));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getClientMessageId()).isEqualTo("racing-id");
        verify(conversationService, never()).updateLastInteraction(any(), any());
        verifyNoInteractions(messageBroadcastService);
    }

    // --------------------------------------------------------------------------- F-21 (autoria)

    @Test
    void ignoresClientRoleAndPersistsServerDerivedUserRole() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setRole("assistant");

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getRole()).isEqualTo("user");
    }

    @Test
    void acceptsMissingRoleAndStillPersistsUser() {
        UUID senderId = UUID.randomUUID();
        Conversation conversation = conversation(ConversationType.DIRECT);
        MessageRequestDTO dto = messageRequest(MessageType.USER_TO_USER);
        dto.setRole(null);

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(rbac.currentUserId()).willReturn(senderId);
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.sendUserMessage(dto);

        assertThat(response.getRole()).isEqualTo("user");
    }

    // -------------------------------------------------------------------- F-20 (ingestão chatbot)

    @Test
    void ingestsChatbotMessageWithAssistantRole() {
        Conversation conversation = conversation(ConversationType.CHAT_BOT);

        ChatbotMessageRequestDTO dto = new ChatbotMessageRequestDTO();
        dto.setConversationId("c-1");
        dto.setContent("Resposta do chatbot");

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        MessageResponseDTO response = messageService.ingestChatbotMessage(dto);

        assertThat(response.getMessageType()).isEqualTo(MessageType.CHATBOT_TO_USER);
        assertThat(response.getRole()).isEqualTo("assistant");
        verify(conversationService).requireActive(conversation);
        verify(conversationService).updateLastInteraction(eq(conversation), any(Instant.class));
    }

    @Test
    void rejectsChatbotMessageIntoDirectConversation() {
        Conversation conversation = conversation(ConversationType.DIRECT);
        ChatbotMessageRequestDTO dto = chatbotMessageRequest();

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);

        assertThatThrownBy(() -> messageService.ingestChatbotMessage(dto))
                .isInstanceOfSatisfying(InvalidFieldException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(messageRepository, messageBroadcastService);
        verify(conversationService, never()).nextSequence(any());
        verify(conversationService, never()).requireActive(any());
    }

    @Test
    void rejectsChatbotMessageIntoGroupConversation() {
        Conversation conversation = conversation(ConversationType.GROUP);
        ChatbotMessageRequestDTO dto = chatbotMessageRequest();

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);

        assertThatThrownBy(() -> messageService.ingestChatbotMessage(dto))
                .isInstanceOf(InvalidFieldException.class);

        verifyNoInteractions(messageRepository, messageBroadcastService);
    }

    @Test
    void rejectsChatbotMessageWhenConversationIsDeactivated() {
        Conversation conversation = conversation(ConversationType.CHAT_BOT);
        ChatbotMessageRequestDTO dto = chatbotMessageRequest();

        given(conversationService.requireEntityById("c-1")).willReturn(conversation);
        doThrow(new BusinessRuleException("A conversa está desativada e não aceita novas mensagens."))
                .when(conversationService).requireActive(conversation);

        assertThatThrownBy(() -> messageService.ingestChatbotMessage(dto))
                .isInstanceOf(BusinessRuleException.class);

        verifyNoInteractions(messageRepository, messageBroadcastService);
    }

    // --------------------------------------------------------------------------------- apoio

    private MessageRequestDTO messageRequest(MessageType messageType) {
        MessageRequestDTO dto = new MessageRequestDTO();
        dto.setConversationId("c-1");
        dto.setMessageType(messageType);
        dto.setRole("user");
        dto.setContent("Olá");
        return dto;
    }

    private ChatbotMessageRequestDTO chatbotMessageRequest() {
        ChatbotMessageRequestDTO dto = new ChatbotMessageRequestDTO();
        dto.setConversationId("c-1");
        dto.setContent("Resposta do chatbot");
        return dto;
    }

    private Conversation conversation(ConversationType type) {
        Conversation conversation = new Conversation();
        conversation.setId("c-1");
        conversation.setConversationType(type);
        conversation.setParticipantIds(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setStartedAt(Instant.now());
        conversation.setLastInteractionAt(Instant.now());
        return conversation;
    }

    private Message message() {
        Message message = new Message();
        message.setId("m-1");
        message.setConversationId("c-1");
        message.setSenderId(UUID.randomUUID());
        message.setRole("user");
        message.setMessageType(MessageType.USER_TO_GROUP);
        message.setContent("Olá");
        message.setTimestamp(Instant.now());
        return message;
    }
}
