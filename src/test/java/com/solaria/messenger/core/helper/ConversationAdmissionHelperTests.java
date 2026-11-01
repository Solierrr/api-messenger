package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ClientSession;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.repository.MessageRepository;

/**
 * Cobre a readmissão: quem volta ao grupo reaproveita o estado pessoal que já tinha e perde a marca
 * {@code left_at_version}; quem entra pela primeira vez ganha um estado novo sem marca, e quem já participava não é
 * tocado.
 */
class ConversationAdmissionHelperTests {

    private static final String GROUP = "group-1";
    private static final UUID MANAGER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RETURNING = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEWCOMER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private ConversationRepository conversations;
    private ConversationUserStateRepository userStates;
    private MessageRepository messages;
    private ConversationAdmissionHelper helper;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        userStates = mock(ConversationUserStateRepository.class);
        messages = mock(MessageRepository.class);
        MongoDatabaseFactory databaseFactory = mock(MongoDatabaseFactory.class);
        given(databaseFactory.getSession(any(ClientSessionOptions.class))).willReturn(mock(ClientSession.class));
        helper = new ConversationAdmissionHelper(conversations, userStates, messages, mock(CommunityRepository.class),
                databaseFactory);

        Conversation current = new Conversation();
        current.setId(GROUP);
        current.setConversationType(ConversationType.GROUP);
        current.setVersion(1L);
        current.setManagerUserId(MANAGER);
        current.setParticipantIds(new LinkedHashSet<>(Set.of(MANAGER)));
        given(conversations.findById(GROUP)).willReturn(Optional.of(current));
        given(messages.findFirstByConversationIdOrderBySequenceDesc(GROUP)).willReturn(Optional.empty());
        given(conversations.updateParticipantsIfCurrent(eq(GROUP), eq(1L), any(), eq(MANAGER), any(), anyLong()))
                .willReturn(1L);
        given(userStates.findByConversationIdAndUserId(GROUP, MANAGER)).willReturn(Optional.of(validState(MANAGER, null)));
    }

    @Test
    void returningUserKeepsTheOldStateAndLosesTheLeftMark() {
        given(userStates.findByConversationIdAndUserId(GROUP, RETURNING)).willReturn(Optional.of(validState(RETURNING, 40L)));

        helper.admitParticipants(GROUP, 1L, MANAGER, Set.of(RETURNING));

        verify(userStates).clearLeft(GROUP, RETURNING);
        verify(userStates, never()).insert(any());
        verify(userStates, never()).clearLeft(GROUP, MANAGER);
    }

    @Test
    void firstTimeUserGetsAFreshStateWithoutALeftMark() {
        given(userStates.findByConversationIdAndUserId(GROUP, NEWCOMER)).willReturn(Optional.empty());

        helper.admitParticipants(GROUP, 1L, MANAGER, Set.of(NEWCOMER));

        ArgumentCaptor<ConversationUserState> inserted = ArgumentCaptor.forClass(ConversationUserState.class);
        verify(userStates).insert(inserted.capture());
        assertThat(inserted.getValue().getUserId()).isEqualTo(NEWCOMER);
        assertThat(inserted.getValue().getLeftAtVersion()).isNull();
        verify(userStates, never()).clearLeft(any(), any());
    }

    private static ConversationUserState validState(UUID user, Long leftAtVersion) {
        ConversationUserState state = new ConversationUserState();
        state.setConversationId(GROUP);
        state.setUserId(user);
        state.setBaselineSequence(0);
        state.setLastReadSequence(0);
        state.setMuted(false);
        state.setArchived(false);
        state.setLeftAtVersion(leftAtVersion);
        return state;
    }
}
