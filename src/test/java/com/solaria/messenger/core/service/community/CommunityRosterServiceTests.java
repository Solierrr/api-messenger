package com.solaria.messenger.core.service.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.core.ProjectAuthorityClient;
import com.solaria.messenger.core.integration.core.dto.ProjectAccessResponseDTO;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.conversation.ConversationChangeNotifier;
import com.solaria.messenger.core.service.conversation.ConversationQueryService;
import com.solaria.messenger.core.service.port.SessionRevoker;

@ExtendWith(MockitoExtension.class)
class CommunityRosterServiceTests {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private ProjectAuthorityClient projectAuthorityClient;

    @Mock
    private ConversationQueryService queries;

    @Mock
    private ConversationChangeNotifier stateEvents;

    @Mock
    private SessionRevoker sessionRevoker;

    private CommunityRosterService roster;

    @BeforeEach
    void setUp() {
        roster = new CommunityRosterService(communityRepository, projectAuthorityClient, queries, stateEvents,
                new ConversationMutationGuard(), sessionRevoker);
    }

    private static ProjectCommunity community(CommunityMember... members) {
        ProjectCommunity community = new ProjectCommunity();
        community.setId("community-1");
        community.setVersion(3L);
        community.setProjectId(PROJECT);
        community.setStatus(CommunityStatus.ACTIVE);
        community.setMembers(new ArrayList<>(List.of(members)));
        return community;
    }

    private static ProjectAccessResponseDTO access(boolean readOnly, Map<UUID, String> members) {
        return new ProjectAccessResponseDTO("OWNER", readOnly, members, "Projeto");
    }

    @Test
    void mapsProjectRolesToCommunityRoles() {
        assertThat(CommunityRosterService.roleOf("OWNER")).isEqualTo(CommunityRole.OWNER);
        assertThat(CommunityRosterService.roleOf("MANAGER")).isEqualTo(CommunityRole.ADMIN);
        assertThat(CommunityRosterService.roleOf("MEMBER")).isEqualTo(CommunityRole.MEMBER);
        assertThat(CommunityRosterService.roleOf("VIEWER")).isEqualTo(CommunityRole.VIEWER);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesTheRosterRevokesFormerMembersAndArchivesReadOnlyProjects() {
        Instant joined = Instant.parse("2026-01-01T00:00:00Z");
        ProjectCommunity current = community(CommunityMember.of(OWNER, CommunityRole.OWNER, joined),
                CommunityMember.of(FORMER, CommunityRole.MEMBER, joined));
        Map<UUID, String> members = new LinkedHashMap<>();
        members.put(OWNER, "OWNER");
        members.put(VIEWER, "VIEWER");
        given(communityRepository.updateRosterIfCurrent(eq("community-1"), eq(3L), anyList(),
                eq(CommunityStatus.ARCHIVED), any(), eq(4L))).willReturn(1L);
        given(communityRepository.findById("community-1")).willReturn(Optional.of(current));

        roster.apply(current, access(true, members));

        ArgumentCaptor<List<CommunityMember>> written = ArgumentCaptor.forClass(List.class);
        verify(communityRepository).updateRosterIfCurrent(eq("community-1"), eq(3L), written.capture(),
                eq(CommunityStatus.ARCHIVED), any(), eq(4L));
        assertThat(written.getValue()).extracting(CommunityMember::getUserId).containsExactly(OWNER, VIEWER);
        assertThat(written.getValue()).extracting(CommunityMember::getRole)
                .containsExactly(CommunityRole.OWNER, CommunityRole.VIEWER);
        assertThat(written.getValue().getFirst().getJoinedAt()).isEqualTo(joined);
        verify(sessionRevoker).revokeUser(FORMER);
        verify(sessionRevoker, never()).revokeUser(OWNER);
    }

    @Test
    void unchangedRosterIsNotWritten() {
        ProjectCommunity current = community(CommunityMember.of(OWNER, CommunityRole.OWNER, Instant.now()));

        roster.apply(current, access(false, Map.of(OWNER, "OWNER")));

        verify(communityRepository, never()).updateRosterIfCurrent(anyString(), anyLong(), anyList(), any(), any(),
                anyLong());
        verify(sessionRevoker, never()).revokeUser(any());
    }

    @Test
    void userDeniedByTheProjectLeavesTheCommunity() {
        ProjectCommunity current = community(CommunityMember.of(OWNER, CommunityRole.OWNER, Instant.now()),
                CommunityMember.of(FORMER, CommunityRole.MEMBER, Instant.now()));
        given(projectAuthorityClient.access(PROJECT, FORMER)).willThrow(new UnauthorizedAccessException("Removido"));
        given(communityRepository.updateMembersIfCurrent(eq("community-1"), eq(3L), anyList(), any(), eq(4L)))
                .willReturn(1L);
        given(communityRepository.findById("community-1")).willReturn(Optional.of(current));

        assertThatThrownBy(() -> roster.sync(current, FORMER)).isInstanceOf(UnauthorizedAccessException.class);

        verify(communityRepository).updateMembersIfCurrent(eq("community-1"), eq(3L),
                eq(List.of(current.getMembers().getFirst())), any(), eq(4L));
        verify(sessionRevoker).revokeUser(FORMER);
    }
}
