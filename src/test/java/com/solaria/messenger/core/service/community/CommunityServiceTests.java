package com.solaria.messenger.core.service.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.core.ProjectAuthorityClient;
import com.solaria.messenger.core.integration.core.dto.ProjectAccessResponseDTO;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.conversation.ConversationCreationService;
import com.solaria.messenger.core.service.conversation.ConversationQueryService;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTests {

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private ConversationQueryService queries;

    @Mock
    private ConversationCreationService creations;

    @Mock
    private RbacAuthorizationService rbac;

    @Mock
    private ProjectAuthorityClient projectAuthorityClient;

    @Mock
    private CommunityRosterService roster;

    @InjectMocks
    private CommunityService service;

    private static ProjectCommunity community(CommunityRole role) {
        ProjectCommunity community = new ProjectCommunity();
        community.setId("community-1");
        community.setProjectId(PROJECT);
        community.setStatus(CommunityStatus.ACTIVE);
        community.setMembers(new ArrayList<>(List.of(CommunityMember.of(USER, role, Instant.now()))));
        return community;
    }

    private static CommunityRequestDTO request() {
        CommunityRequestDTO dto = new CommunityRequestDTO();
        dto.setProjectId(PROJECT);
        dto.setMemberIds(Set.of(OTHER));
        return dto;
    }

    @Test
    void createsTheCommunityFromTheProjectRosterIgnoringRequestedMembers() {
        given(rbac.currentUserId()).willReturn(USER);
        given(projectAuthorityClient.access(PROJECT, USER))
                .willReturn(new ProjectAccessResponseDTO("VIEWER", true, Map.of(USER, "VIEWER"), "Projeto"));
        given(communityRepository.findByProjectId(PROJECT)).willReturn(Optional.empty());
        given(communityRepository.save(any(ProjectCommunity.class))).willAnswer(call -> call.getArgument(0));
        given(roster.apply(any(ProjectCommunity.class), any())).willAnswer(call -> call.getArgument(0));

        service.createCommunity(request());

        ArgumentCaptor<ProjectCommunity> saved = ArgumentCaptor.forClass(ProjectCommunity.class);
        verify(communityRepository).save(saved.capture());
        assertThat(saved.getValue().getMembers()).extracting(CommunityMember::getUserId).containsExactly(USER);
        assertThat(saved.getValue().getMembers().getFirst().getRole()).isEqualTo(CommunityRole.VIEWER);
        assertThat(saved.getValue().getStatus()).isEqualTo(CommunityStatus.ARCHIVED);
    }

    @Test
    void creatingAnExistingCommunitySyncsAndReturnsIt() {
        ProjectCommunity existing = community(CommunityRole.MEMBER);
        ProjectAccessResponseDTO access = new ProjectAccessResponseDTO("MEMBER", false, Map.of(USER, "MEMBER"), "Projeto");
        given(rbac.currentUserId()).willReturn(USER);
        given(projectAuthorityClient.access(PROJECT, USER)).willReturn(access);
        given(communityRepository.findByProjectId(PROJECT)).willReturn(Optional.of(existing));
        given(roster.apply(existing, access)).willReturn(existing);

        assertThat(service.createCommunity(request()).getId()).isEqualTo("community-1");
        verify(communityRepository, never()).save(any());
    }

    @Test
    void viewersCannotStartSubgroups() {
        ProjectCommunity community = community(CommunityRole.VIEWER);
        given(rbac.currentUserId()).willReturn(USER);
        given(communityRepository.findById("community-1")).willReturn(Optional.of(community));
        given(roster.sync(community, USER)).willReturn(community);

        assertThatThrownBy(() -> service.startConversation("community-1", "Elétrica", Set.of(OTHER)))
                .isInstanceOf(UnauthorizedAccessException.class);
        verify(creations, never()).createCommunityGroupConversation(any(), any(), any());
    }

    @Test
    void membersAndStatusAreManagedByTheProject() {
        assertThatThrownBy(() -> service.updateCommunity("community-1", new CommunityUpdateRequestDTO()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void myCommunitiesLeaveOutProjectsTheUserLeft() {
        ProjectCommunity kept = community(CommunityRole.MEMBER);
        ProjectCommunity left = community(CommunityRole.MEMBER);
        left.setId("community-2");
        given(rbac.currentUserId()).willReturn(USER);
        given(communityRepository.findAllByMemberId(USER)).willReturn(List.of(kept, left));
        given(roster.sync(kept, USER)).willReturn(kept);
        given(roster.sync(left, USER)).willThrow(new UnauthorizedAccessException("Removido"));

        assertThat(service.findMine()).extracting("id").containsExactly("community-1");
    }
}
