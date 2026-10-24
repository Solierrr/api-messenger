package com.solaria.messenger.service;

import com.solaria.messenger.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.dto.request.CommunityMembersRequestDTO;
import com.solaria.messenger.dto.request.CommunityRequestDTO;
import com.solaria.messenger.dto.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.dto.response.CommunityMemberDTO;
import com.solaria.messenger.dto.response.CommunityResponseDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.BusinessRuleException;
import com.solaria.messenger.exception.InvalidFieldException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.exception.UnauthorizedAccessException;
import com.solaria.messenger.integration.ProjectAuthorityClient;
import com.solaria.messenger.model.CommunityMember;
import com.solaria.messenger.model.ProjectCommunity;
import com.solaria.messenger.model.enums.CommunityRole;
import com.solaria.messenger.model.enums.CommunityStatus;
import com.solaria.messenger.repository.CommunityRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * service da comunidade do projeto Toda operação é autorizada pelo papel ({@link CommunityRole}) do
 * usuário
 */
@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final ConversationService conversationService;
    private final RbacAuthorizationService rbac;
    private final ProjectAuthorityClient projectAuthorityClient;

    public CommunityService(
            CommunityRepository communityRepository,
            ConversationService conversationService,
            RbacAuthorizationService rbac,
            ProjectAuthorityClient projectAuthorityClient) {
        this.communityRepository = communityRepository;
        this.conversationService = conversationService;
        this.rbac = rbac;
        this.projectAuthorityClient = projectAuthorityClient;
    }

    public CommunityResponseDTO createCommunity(CommunityRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        var authority = projectAuthorityClient.access(dto.getProjectId(), currentUserId);
        var existing = communityRepository.findByProjectId(dto.getProjectId());
        if (existing.isPresent()) {
            sync(existing.get(), authority);
            return toResponse(existing.get());
        }
        Instant now = Instant.now();
        List<CommunityMember> members =
                authority.members().entrySet().stream()
                        .map(e -> CommunityMember.of(e.getKey(), mappedRole(e.getValue()), now))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        ProjectCommunity community = new ProjectCommunity();
        community.setProjectId(dto.getProjectId());
        community.setMembers(members);
        community.setStatus(CommunityStatus.ACTIVE);
        community.setCreatedBy(currentUserId);
        community.setCreatedAt(now);
        community.setUpdatedAt(now);

        communityRepository.save(community);
        sync(community, authority);
        return toResponse(community);
    }

    public CommunityResponseDTO findById(String id) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community);
        return toResponse(community);
    }

    public CommunityResponseDTO findByProjectId(UUID projectId) {
        ProjectCommunity community =
                communityRepository
                        .findByProjectId(projectId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Comunidade não encontrada para o projeto: "
                                                        + projectId));
        requireMember(community);
        return toResponse(community);
    }

    public List<CommunityResponseDTO> findMine() {
        return communityRepository.findAllByMemberId(rbac.currentUserId()).stream()
                .filter(
                        c -> {
                            try {
                                requireMember(c);
                                return true;
                            } catch (com.solaria.messenger.exception.BusinessException e) {
                                return false;
                            }
                        })
                .map(this::toResponse)
                .toList();
    }

    public CommunityResponseDTO updateCommunity(String id, CommunityUpdateRequestDTO dto) {
        throw new BusinessRuleException("Gerencie participantes e situação pela página do projeto");
    }

    public CommunityResponseDTO addMembers(String id, CommunityMembersRequestDTO dto) {
        throw new BusinessRuleException("Gerencie participantes e situação pela página do projeto");
    }

    public CommunityResponseDTO removeMember(String id, UUID userId) {
        throw new BusinessRuleException("Gerencie participantes e situação pela página do projeto");
    }

    // conversas da comunidade

    public ConversationResponseDTO startConversation(
            String id, CommunityConversationRequestDTO dto) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community);
        requireActive(community);
        projectAuthorityClient.write(community.getProjectId(), rbac.currentUserId());

        UUID currentUserId = rbac.currentUserId();
        Set<UUID> memberIds = community.memberIds();

        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(currentUserId);
        if (dto.getParticipantIds() == null || dto.getParticipantIds().isEmpty()) {
            participants.addAll(memberIds);
        } else {
            for (UUID participantId : dto.getParticipantIds()) {
                if (participantId == null) {
                    continue;
                }
                if (!memberIds.contains(participantId)) {
                    throw new BusinessRuleException(
                            "Todos os participantes precisam ser membros da comunidade: "
                                    + participantId);
                }
                participants.add(participantId);
            }
        }

        if (participants.size() < 2) {
            throw new InvalidFieldException(
                    "Um grupo precisa de ao menos 2 participantes distintos.");
        }

        return conversationService.createCommunityGroupConversation(
                community.getId(), dto.getTitle(), currentUserId, participants);
    }

    public List<ConversationResponseDTO> listConversations(String id) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community);
        return conversationService.findByCommunity(community.getId());
    }

    public ProjectCommunity requireEntityById(String id) {
        return communityRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Comunidade não encontrada com id: " + id));
    }

    private void requireMember(ProjectCommunity community) {
        sync(
                community,
                projectAuthorityClient.access(community.getProjectId(), rbac.currentUserId()));
    }

    private CommunityRole mappedRole(String role) {
        return switch (role) {
            case "OWNER" -> CommunityRole.OWNER;
            case "MANAGER" -> CommunityRole.ADMIN;
            case "VIEWER" -> CommunityRole.VIEWER;
            default -> CommunityRole.MEMBER;
        };
    }

    private void sync(ProjectCommunity c, ProjectAuthorityClient.Access a) {
        var now = Instant.now();
        c.setMembers(
                a.members().entrySet().stream()
                        .map(e -> CommunityMember.of(e.getKey(), mappedRole(e.getValue()), now))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
        c.setStatus(a.readOnly() ? CommunityStatus.ARCHIVED : CommunityStatus.ACTIVE);
        c.setUpdatedAt(now);
        communityRepository.save(c);
        conversationService.ensureGeneral(
                c.getId(), a.name(), c.getCreatedBy(), a.members().keySet());
    }

    private void requireAdmin(ProjectCommunity community) {
        if (!community.isAdmin(rbac.currentUserId())) {
            throw new UnauthorizedAccessException(
                    "Esta ação exige papel OWNER ou ADMIN na comunidade.");
        }
    }

    private void requireOwner(ProjectCommunity community) {
        if (!community.isOwner(rbac.currentUserId())) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER na comunidade.");
        }
    }

    private void requireActive(ProjectCommunity community) {
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }
    }

    private CommunityResponseDTO toResponse(ProjectCommunity community) {
        List<CommunityMemberDTO> members =
                community.getMembers() == null
                        ? List.of()
                        : community.getMembers().stream()
                                .map(
                                        m ->
                                                CommunityMemberDTO.builder()
                                                        .userId(m.getUserId())
                                                        .role(m.getRole())
                                                        .joinedAt(m.getJoinedAt())
                                                        .build())
                                .toList();

        return CommunityResponseDTO.builder()
                .id(community.getId())
                .projectId(community.getProjectId())
                .members(members)
                .status(community.getStatus())
                .createdBy(community.getCreatedBy())
                .createdAt(community.getCreatedAt())
                .updatedAt(community.getUpdatedAt())
                .build();
    }
}
