package com.solaria.messenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.solaria.messenger.dto.request.UserConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.repository.ConversationRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTests {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private RbacAuthorizationService rbac;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createsUserConversationWithActiveStatusAndTimestamps() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        UserConversationRequestDTO dto = new UserConversationRequestDTO();
        dto.setReceiverId(receiverId);

        given(rbac.currentUserId()).willReturn(senderId);
        given(conversationRepository.save(any(Conversation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ConversationResponseDTO response = conversationService.createUserConversation(dto);

        assertThat(response.getSenderId()).isEqualTo(senderId);
        assertThat(response.getReceiverId()).isEqualTo(receiverId);
        assertThat(response.getConversationType()).isEqualTo(ConversationType.USER_CONVERSATION);
        assertThat(response.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(response.getStartedAt()).isNotNull();
        assertThat(response.getLastInteractionAt()).isEqualTo(response.getStartedAt());
    }

    @Test
    void getsConversationById() {
        Conversation conversation = conversation();
        given(conversationRepository.findById("conversation-1")).willReturn(Optional.of(conversation));

        Conversation foundConversation = conversationService.requireEntityById("conversation-1");

        assertThat(foundConversation).isSameAs(conversation);
    }

    @Test
    void throwsNotFoundWhenConversationDoesNotExist() {
        given(conversationRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.requireEntityById("missing"))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
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
    void getsConversationsForCurrentUser() {
        UUID currentUserId = UUID.randomUUID();
        List<Conversation> conversations = List.of(conversation());
        given(rbac.currentUserId()).willReturn(currentUserId);
        given(conversationRepository.findByReceiverIdOrSenderId(currentUserId, currentUserId))
                .willReturn(conversations);

        List<ConversationResponseDTO> foundConversations = conversationService.findMine();

        assertThat(foundConversations).hasSize(1);
        assertThat(foundConversations.get(0).getId()).isEqualTo(conversations.get(0).getId());
    }

    private Conversation conversation() {
        return new Conversation("conversation-1", UUID.randomUUID(), UUID.randomUUID(),
                ConversationType.USER_CONVERSATION, null, null, ConversationStatus.ACTIVE,
                Instant.now(), Instant.now());
    }
}
