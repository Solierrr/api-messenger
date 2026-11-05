package com.solaria.messenger.core.usecase.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import com.solaria.messenger.core.contract.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.exception.DuplicateResourceException;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Cobre a criação da conversa do chatbot com e sem {@code conversationId}: o id escolhido vira o id da conversa, uma
 * conversa existente do mesmo usuário é reaproveitada, e a de outro usuário (ou de outro tipo) é conflito.
 */
class CreateChatbotConversationUseCaseTests {

    private static final UUID ME = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String SESSION_ID = "sandbox-0dd2a4d3";

    private ConversationRepository repository;
    private NotificationHelper notifications;
    private CreateChatbotConversationUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationRepository.class);
        notifications = mock(NotificationHelper.class);
        RbacAuthorizationService rbac = mock(RbacAuthorizationService.class);
        given(rbac.currentUserId()).willReturn(ME);
        given(repository.save(any(Conversation.class))).willAnswer(call -> call.getArgument(0));
        useCase = new CreateChatbotConversationUseCase(repository, rbac, notifications);
    }

    private static ChatbotConversationRequestDTO request(String conversationId) {
        ChatbotConversationRequestDTO dto = new ChatbotConversationRequestDTO();
        dto.setUserType("lead");
        dto.setUserDetails(Map.of("threadId", SESSION_ID));
        dto.setConversationId(conversationId);
        return dto;
    }

    private static Conversation stored(ConversationType type, UUID owner) {
        Conversation conversation = new Conversation();
        conversation.setId(SESSION_ID);
        conversation.setConversationType(type);
        conversation.setParticipantIds(new LinkedHashSet<>(Set.of(owner)));
        conversation.setCreatedBy(owner);
        return conversation;
    }

    @Test
    void withoutConversationIdTheIdIsGenerated() {
        ConversationResponseDTO response = useCase.execute(request(null));

        assertThat(response.getId()).matches("^[0-9a-f]{24}$");
        verify(repository, never()).findById(anyString());
    }

    @Test
    void conversationIdBecomesTheIdOfTheNewConversation() {
        given(repository.findById(SESSION_ID)).willReturn(Optional.empty());

        ConversationResponseDTO response = useCase.execute(request(SESSION_ID));

        ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(SESSION_ID);
        assertThat(saved.getValue().getConversationType()).isEqualTo(ConversationType.CHAT_BOT);
        assertThat(response.getId()).isEqualTo(SESSION_ID);
        verify(notifications).publishConversationChanged(SESSION_ID);
    }

    @Test
    void anExistingChatbotConversationOfTheSameUserIsReused() {
        given(repository.findById(SESSION_ID)).willReturn(Optional.of(stored(ConversationType.CHAT_BOT, ME)));

        ConversationResponseDTO response = useCase.execute(request(SESSION_ID));

        assertThat(response.getId()).isEqualTo(SESSION_ID);
        verify(repository, never()).save(any(Conversation.class));
    }

    @Test
    void aConversationOfAnotherUserWithTheSameIdIsAConflict() {
        given(repository.findById(SESSION_ID)).willReturn(Optional.of(stored(ConversationType.CHAT_BOT, OTHER)));

        assertThatThrownBy(() -> useCase.execute(request(SESSION_ID))).isInstanceOf(DuplicateResourceException.class);
        verify(repository, never()).save(any(Conversation.class));
    }

    @Test
    void aConversationOfAnotherTypeWithTheSameIdIsAConflict() {
        given(repository.findById(SESSION_ID)).willReturn(Optional.of(stored(ConversationType.DIRECT, ME)));

        assertThatThrownBy(() -> useCase.execute(request(SESSION_ID))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void whenTwoCallsRaceTheLoserReusesTheConversationTheWinnerCreated() {
        given(repository.findById(SESSION_ID)).willReturn(Optional.empty())
                .willReturn(Optional.of(stored(ConversationType.CHAT_BOT, ME)));
        given(repository.save(any(Conversation.class))).willThrow(new DuplicateKeyException("E11000"));

        ConversationResponseDTO response = useCase.execute(request(SESSION_ID));

        assertThat(response.getId()).isEqualTo(SESSION_ID);
    }

    @Test
    void conversationIdFormatIsValidated() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(request(SESSION_ID))).isEmpty();
        assertThat(validator.validate(request(null))).isEmpty();
        assertThat(validator.validate(request("curto"))).isNotEmpty();
        assertThat(validator.validate(request("com espaco no meio"))).isNotEmpty();
        assertThat(validator.validate(request("../../etc/passwd"))).isNotEmpty();
        assertThat(validator.validate(request("a".repeat(65)))).isNotEmpty();
    }
}
