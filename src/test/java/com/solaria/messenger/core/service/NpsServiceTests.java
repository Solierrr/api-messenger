package com.solaria.messenger.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.solaria.messenger.core.contract.request.NpsRequestDTO;
import com.solaria.messenger.core.contract.response.NpsResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.DuplicateResourceException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Nps;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.NpsClassification;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.NpsRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

@ExtendWith(MockitoExtension.class)
class NpsServiceTests {

    private static final String CONVERSATION_ID = "conv-1";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private NpsRepository npsRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private RbacAuthorizationService rbac;

    @InjectMocks
    private NpsService npsService;

    @ParameterizedTest
    @CsvSource({
            "0,DETRACTOR", "6,DETRACTOR",
            "7,PASSIVE", "8,PASSIVE",
            "9,PROMOTER", "10,PROMOTER"
    })
    void classifiesScoreBoundaries(int score, NpsClassification expected) {
        assertThat(NpsClassification.fromScore(score)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"-1", "11"})
    void rejectsScoreOutOfRange(int score) {
        assertThatThrownBy(() -> NpsClassification.fromScore(score))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void savesNpsWithClassificationAndAuthenticatedUser() {
        givenOwnChatbotConversation();
        given(rbac.currentUserId()).willReturn(USER_ID);
        given(npsRepository.existsByConversationId(CONVERSATION_ID)).willReturn(false);
        given(npsRepository.save(any(Nps.class))).willAnswer(invocation -> invocation.getArgument(0));

        NpsResponseDTO response = npsService.create(request(9));

        ArgumentCaptor<Nps> captor = ArgumentCaptor.forClass(Nps.class);
        verify(npsRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getConversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.getClassification()).isEqualTo(NpsClassification.PROMOTER);
        assertThat(response.getScore()).isEqualTo(9);
    }

    @Test
    void rejectsConversationFromAnotherUser() {
        given(rbac.currentUserId()).willReturn(USER_ID);
        given(conversationRepository.findById(CONVERSATION_ID))
                .willReturn(Optional.of(chatbotConversation(UUID.randomUUID())));
        doThrow(new UnauthorizedAccessException("O objeto da operação não foi encontrado."))
                .when(rbac).requireParticipant(any(Set.class));

        assertThatThrownBy(() -> npsService.create(request(10)))
                .isInstanceOf(UnauthorizedAccessException.class);
        verify(npsRepository, never()).save(any(Nps.class));
    }

    @Test
    void rejectsUnknownConversation() {
        given(rbac.currentUserId()).willReturn(USER_ID);
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> npsService.create(request(10)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(npsRepository, never()).save(any(Nps.class));
    }

    @Test
    void rejectsNonChatbotConversation() {
        given(rbac.currentUserId()).willReturn(USER_ID);
        Conversation direct = chatbotConversation(USER_ID);
        direct.setConversationType(ConversationType.DIRECT);
        given(conversationRepository.findById(CONVERSATION_ID)).willReturn(Optional.of(direct));

        assertThatThrownBy(() -> npsService.create(request(10)))
                .isInstanceOf(BusinessRuleException.class);
        verify(npsRepository, never()).save(any(Nps.class));
    }

    @Test
    void convertsUniqueIndexViolationFromConcurrentRequestsToConflict() {
        givenOwnChatbotConversation();
        given(rbac.currentUserId()).willReturn(USER_ID);
        given(npsRepository.existsByConversationId(CONVERSATION_ID)).willReturn(false);
        given(npsRepository.save(any(Nps.class))).willThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> npsService.create(request(10)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsSecondResponseForSameConversation() {
        givenOwnChatbotConversation();
        given(rbac.currentUserId()).willReturn(USER_ID);
        given(npsRepository.existsByConversationId(CONVERSATION_ID)).willReturn(true);

        assertThatThrownBy(() -> npsService.create(request(10)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(npsRepository, never()).save(any(Nps.class));
    }

    private void givenOwnChatbotConversation() {
        given(conversationRepository.findById(CONVERSATION_ID))
                .willReturn(Optional.of(chatbotConversation(USER_ID)));
    }

    private Conversation chatbotConversation(UUID owner) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setConversationType(ConversationType.CHAT_BOT);
        conversation.setParticipantIds(new LinkedHashSet<>(Set.of(owner)));
        return conversation;
    }

    private NpsRequestDTO request(int score) {
        NpsRequestDTO dto = new NpsRequestDTO();
        dto.setConversationId(CONVERSATION_ID);
        dto.setScore(score);
        return dto;
    }
}
