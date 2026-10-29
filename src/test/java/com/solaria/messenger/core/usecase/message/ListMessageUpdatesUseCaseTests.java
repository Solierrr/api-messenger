package com.solaria.messenger.core.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.contract.response.MessageUpdatesResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

/**
 * Cobre o catch-up: ponto de partida sem {@code since}, escopo de conversas (legíveis mais perdidas), paginação, cursor
 * e validação do {@code since}.
 */
@ExtendWith(MockitoExtension.class)
class ListMessageUpdatesUseCaseTests {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private MessageRepository messages;
    @Mock
    private ConversationUserStateRepository userStates;
    @Mock
    private RbacAuthorizationService rbac;
    @Mock
    private ConversationAccessHelper access;
    @Mock
    private ConversationSyncClock clock;

    private ListMessageUpdatesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListMessageUpdatesUseCase(messages, userStates, rbac, access, clock, new ConversationMutationGuard());
    }

    @Test
    void withoutSinceReturnsOnlyTheStartingPoint() {
        given(clock.currentVersion()).willReturn(500L);
        given(clock.epoch()).willReturn("epoch-1");

        MessageUpdatesResponseDTO response = useCase.execute(null, null);

        assertThat(response.syncEpoch()).isEqualTo("epoch-1");
        assertThat(response.cursor()).isEqualTo(500L);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.messages()).isEmpty();
        verifyNoInteractions(messages, userStates, access, rbac);
    }

    @Test
    void negativeSinceIsInvalidField() {
        assertThatThrownBy(() -> useCase.execute(-1L, null))
                .isInstanceOfSatisfying(InvalidFieldException.class,
                        failure -> assertThat(failure.getErrorCode()).isEqualTo("INVALID_FIELD"));
        verifyNoInteractions(messages, userStates, access);
    }

    @Test
    void sinceZeroIsValidAndAsksForEverythingAfterVersionZero() {
        givenScope(Set.of("conv-a"), List.of());
        given(clock.epoch()).willReturn("epoch-1");
        given(messages.findUpdates(Set.of("conv-a"), Map.of(), 0L, 101)).willReturn(List.of());

        MessageUpdatesResponseDTO response = useCase.execute(0L, null);

        assertThat(response.messages()).isEmpty();
        assertThat(response.cursor()).isZero();
    }

    @Test
    void returnsReadableConversationsAndTheLostOnesUpToTheirLeftVersion() {
        givenScope(Set.of("conv-a"), List.of(state("conv-b", 12L)));
        given(clock.epoch()).willReturn("epoch-1");
        given(messages.findUpdates(Set.of("conv-a"), Map.of("conv-b", 12L), 5L, 101))
                .willReturn(List.of(message("conv-b", 6), message("conv-a", 10), message("conv-b", 11)));

        MessageUpdatesResponseDTO response = useCase.execute(5L, null);

        assertThat(response.syncEpoch()).isEqualTo("epoch-1");
        assertThat(response.messages()).extracting(dto -> dto.getChangeVersion()).containsExactly(6L, 10L, 11L);
        assertThat(response.cursor()).isEqualTo(11L);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void conversationThatIsStillReadableIgnoresItsLeftMark() {
        // readmitido sem limpeza ou saída de comunidade em curso: quem lê hoje não tem corte
        givenScope(Set.of("conv-a"), List.of(state("conv-a", 3L)));
        given(clock.epoch()).willReturn("epoch-1");
        given(messages.findUpdates(Set.of("conv-a"), Map.of(), 1L, 101)).willReturn(List.of());

        useCase.execute(1L, null);

        verify(messages).findUpdates(Set.of("conv-a"), Map.of(), 1L, 101);
    }

    @Test
    void emptyPageKeepsTheReceivedSinceAsCursor() {
        givenScope(Set.of(), List.of());
        given(clock.epoch()).willReturn("epoch-1");
        given(messages.findUpdates(anySet(), anyMap(), anyLong(), anyInt())).willReturn(List.of());

        MessageUpdatesResponseDTO response = useCase.execute(77L, null);

        assertThat(response.cursor()).isEqualTo(77L);
        assertThat(response.hasMore()).isFalse();
    }

    @Test
    void pageIsTrimmedToTheLimitAndHasMoreComesFromTheExtraMessage() {
        givenScope(Set.of("conv-a"), List.of());
        given(clock.epoch()).willReturn("epoch-1");
        // limit 3 -> a consulta pede 4; voltaram 4, então há mais e o cursor é a terceira
        given(messages.findUpdates(Set.of("conv-a"), Map.of(), 0L, 4)).willReturn(
                LongStream.rangeClosed(1, 4).mapToObj(version -> message("conv-a", version)).toList());

        MessageUpdatesResponseDTO response = useCase.execute(0L, 3);

        assertThat(response.messages()).extracting(dto -> dto.getChangeVersion()).containsExactly(1L, 2L, 3L);
        assertThat(response.cursor()).isEqualTo(3L);
        assertThat(response.hasMore()).isTrue();
    }

    @Test
    void limitIsClampedBetweenOneAndFiveHundred() {
        givenScope(Set.of("conv-a"), List.of());
        given(clock.epoch()).willReturn("epoch-1");
        given(messages.findUpdates(anySet(), anyMap(), anyLong(), anyInt())).willReturn(List.of());

        useCase.execute(0L, 0);
        useCase.execute(0L, 100_000);

        verify(messages).findUpdates(Set.of("conv-a"), Map.of(), 0L, 2);
        verify(messages).findUpdates(Set.of("conv-a"), Map.of(), 0L, 501);
    }

    private void givenScope(Set<String> readable, List<ConversationUserState> left) {
        given(rbac.currentUserId()).willReturn(USER);
        given(access.readableConversationIds(USER)).willReturn(readable);
        given(userStates.findLeftAfter(eq(USER), anyLong())).willReturn(left);
    }

    private static ConversationUserState state(String conversationId, long leftAtVersion) {
        ConversationUserState state = new ConversationUserState();
        state.setConversationId(conversationId);
        state.setUserId(USER);
        state.setLeftAtVersion(leftAtVersion);
        return state;
    }

    private static Message message(String conversationId, long changeVersion) {
        Message message = new Message();
        message.setId("msg-" + changeVersion);
        message.setConversationId(conversationId);
        message.setChangeVersion(changeVersion);
        return message;
    }
}
