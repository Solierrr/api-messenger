package com.solaria.messenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.DirectConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.repository.CommunityRepository;
import com.solaria.messenger.repository.ConversationRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTests {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RbacAuthorizationService rbac;

    @InjectMocks
    private ConversationService conversationService;

    @Test
    void createsDirectConversationWithActiveStatusAndTimestamps() {
        UUID senderId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        DirectConversationRequestDTO dto = new DirectConversationRequestDTO();
        dto.setRecipientId(recipientId);

        given(rbac.currentUserId()).willReturn(senderId);
        given(conversationRepository.findByConversationTypeAndParticipantIdsContaining(
                ConversationType.DIRECT, senderId)).willReturn(List.of());
        given(conversationRepository.save(any(Conversation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ConversationResponseDTO response = conversationService.createDirectConversation(dto);

        assertThat(response.getCreatedBy()).isEqualTo(senderId);
        assertThat(response.getParticipantIds()).containsExactly(senderId, recipientId);
        assertThat(response.getConversationType()).isEqualTo(ConversationType.DIRECT);
        assertThat(response.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(response.getStartedAt()).isNotNull();
        assertThat(response.getLastInteractionAt()).isEqualTo(response.getStartedAt());
    }

    @Test
    void createsChatbotConversationWithEnvironment() {
        Environment environment = Environment.LOCAL;

        ChatbotConversationRequestDTO dto = new ChatbotConversationRequestDTO();
        dto.setEnvironment(environment);
        dto.setUserType("fornecedor");

        UUID currentUserId = UUID.randomUUID();
        given(rbac.currentUserId()).willReturn(currentUserId);
        given(conversationRepository.save(any(Conversation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ConversationResponseDTO response = conversationService.createChatbotConversation(dto);

        assertThat(response.getCreatedBy()).isEqualTo(currentUserId);
        assertThat(response.getParticipantIds()).containsExactly(currentUserId);
        assertThat(response.getConversationType()).isEqualTo(ConversationType.CHAT_BOT);
        assertThat(response.getEnvironment()).isEqualTo(Environment.LOCAL);
        assertThat(response.getUserType()).isEqualTo("fornecedor");
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
        given(conversationRepository.findByParticipantIdsContainingOrderByLastInteractionAtDesc(currentUserId))
                .willReturn(conversations);

        List<ConversationResponseDTO> foundConversations = conversationService.findMine();

        assertThat(foundConversations).hasSize(1);
        assertThat(foundConversations.get(0).getId()).isEqualTo(conversations.get(0).getId());
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId("conversation-1");
        conversation.setParticipantIds(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        conversation.setCreatedBy(UUID.randomUUID());
        conversation.setConversationType(ConversationType.DIRECT);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setStartedAt(Instant.now());
        conversation.setLastInteractionAt(Instant.now());

        return conversation;
    }
}
