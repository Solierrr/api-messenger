package com.solaria.messenger.core.service.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationMutationGuard;

@ExtendWith(MockitoExtension.class)
class ProjectGeneralConversationServiceTests {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();
    private static final String ID = "project-general-community-1";

    @Mock
    private ConversationRepository conversations;

    @Mock
    private ConversationAdmissionService admissions;

    @Mock
    private ConversationChangeNotifier stateEvents;

    private ProjectGeneralConversationService general;

    @BeforeEach
    void setUp() {
        general = new ProjectGeneralConversationService(conversations, admissions, stateEvents,
                new ConversationMutationGuard());
    }

    private static ProjectCommunity community(CommunityStatus status, UUID... members) {
        ProjectCommunity community = new ProjectCommunity();
        community.setId("community-1");
        community.setCreatedBy(OWNER);
        community.setStatus(status);
        List<CommunityMember> list = new ArrayList<>();
        for (UUID member : members) {
            list.add(CommunityMember.of(member, member.equals(OWNER) ? CommunityRole.OWNER : CommunityRole.VIEWER,
                    Instant.now()));
        }
        community.setMembers(list);
        return community;
    }

    @Test
    void createsTheGeneralConversationWithEveryMember() {
        given(conversations.findById(ID)).willReturn(Optional.empty());

        general.ensure(community(CommunityStatus.ACTIVE, OWNER, VIEWER), "Usina");

        ArgumentCaptor<Conversation> created = ArgumentCaptor.forClass(Conversation.class);
        verify(admissions).createWithInitialStates(created.capture());
        assertThat(created.getValue().getId()).isEqualTo(ID);
        assertThat(created.getValue().isProjectGeneral()).isTrue();
        assertThat(created.getValue().getConversationType()).isEqualTo(ConversationType.GROUP);
        assertThat(created.getValue().getTitle()).isEqualTo("Geral · Usina");
        assertThat(created.getValue().getParticipantIds()).containsExactlyInAnyOrder(OWNER, VIEWER);
        assertThat(created.getValue().getManagerUserId()).isEqualTo(OWNER);
        verify(stateEvents).publishConversationChanged(ID);
    }

    @Test
    void waitsForASecondMemberOrAnActiveCommunity() {
        given(conversations.findById(ID)).willReturn(Optional.empty());

        general.ensure(community(CommunityStatus.ACTIVE, OWNER), "Usina");
        general.ensure(community(CommunityStatus.ARCHIVED, OWNER, VIEWER), "Usina");

        verify(admissions, never()).createWithInitialStates(any());
    }

    @Test
    void followsTheMembersAndWarnsWhoLeft() {
        Conversation current = new Conversation();
        current.setId(ID);
        current.setProjectGeneral(true);
        current.setParticipantIds(new LinkedHashSet<>(Set.of(OWNER, FORMER)));
        Conversation updated = new Conversation();
        updated.setId(ID);
        updated.setParticipantIds(new LinkedHashSet<>(Set.of(OWNER, VIEWER)));
        given(conversations.findById(ID)).willReturn(Optional.of(current));
        given(admissions.syncProjectGeneral(ID, Set.of(OWNER, VIEWER), OWNER, "Geral · Usina")).willReturn(updated);

        general.ensure(community(CommunityStatus.ACTIVE, OWNER, VIEWER), "Usina");

        verify(stateEvents).publishRemovedConversation(FORMER, ID);
        verify(stateEvents).publishConversationChanged(ID);
    }
}
