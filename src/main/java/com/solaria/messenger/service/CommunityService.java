package com.solaria.messenger.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.dto.request.CommunityMembersRequestDTO;
import com.solaria.messenger.dto.request.CommunityRequestDTO;
import com.solaria.messenger.dto.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.dto.response.CommunityMemberDTO;
import com.solaria.messenger.dto.response.CommunityResponseDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.BusinessRuleException;
import com.solaria.messenger.exception.DuplicateResourceException;
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

/**
 * service da comunidade do projeto
 * Toda operação é autorizada pelo papel ({@link CommunityRole}) do usuário
 */
@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final ConversationService conversationService;
    private final RbacAuthorizationService rbac;
    private final ProjectAuthorityClient projectAuthorityClient;

    public CommunityService(CommunityRepository communityRepository,
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

        projectAuthorityClient.requireRequester(dto.getProjectId(), currentUserId);

        if (communityRepository.existsByProjectId(dto.getProjectId())) {
            throw new DuplicateResourceException(
                    "Já existe uma comunidade para o projeto: " + dto.getProjectId());
        }

        Instant now = Instant.now();

        List<CommunityMember> members = new ArrayList<>();
        members.add(CommunityMember.of(currentUserId, CommunityRole.OWNER, now));
        if (dto.getMemberIds() != null) {
            dto.getMemberIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !id.equals(currentUserId))
                    .distinct()
                    .forEach(id -> members.add(CommunityMember.of(id, CommunityRole.MEMBER, now)));
        }

        ProjectCommunity community = new ProjectCommunity();
        community.setProjectId(dto.getProjectId());
        community.setMembers(members);
        community.setStatus(CommunityStatus.ACTIVE);
        community.setCreatedBy(currentUserId);
        community.setCreatedAt(now);
        community.setUpdatedAt(now);

        return toResponse(communityRepository.save(community));
    }

    public CommunityResponseDTO findById(String id) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community);
        return toResponse(community);
    }

    public CommunityResponseDTO findByProjectId(UUID projectId) {
        ProjectCommunity community = communityRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comunidade não encontrada para o projeto: " + projectId));
        requireMember(community);
        return toResponse(community);
    }

    public List<CommunityResponseDTO> findMine() {
        return communityRepository.findAllByMemberId(rbac.currentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CommunityResponseDTO updateCommunity(String id, CommunityUpdateRequestDTO dto) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community);

        if (dto.getStatus() != null) {
            requireOwner(community);
            community.setStatus(dto.getStatus());
        }

        community.setUpdatedAt(Instant.now());
        return toResponse(communityRepository.save(community));
    }


    public CommunityResponseDTO addMembers(String id, CommunityMembersRequestDTO dto) {
        ProjectCommunity community = requireEntityById(id);

        CommunityRole role = dto.getRole() != null ? dto.getRole() : CommunityRole.MEMBER;
        if (role == CommunityRole.OWNER) {
            throw new InvalidFieldException("Não é possível atribuir o papel OWNER.");
        }
        if (role == CommunityRole.ADMIN) {
            requireOwner(community);
        } else {
            requireAdmin(community);
        }
        requireActive(community);

        Instant now = Instant.now();
        for (UUID userId : dto.getUserIds()) {
            if (userId == null) {
                continue;
            }
            community.member(userId).ifPresentOrElse(existing -> {
                if (existing.getRole() != CommunityRole.OWNER) {
                    existing.setRole(role);
                }
            }, () -> community.getMembers().add(CommunityMember.of(userId, role, now)));
        }

        community.setUpdatedAt(now);
        return toResponse(communityRepository.save(community));
    }

    public CommunityResponseDTO removeMember(String id, UUID userId) {
        ProjectCommunity community = requireEntityById(id);
        UUID currentUserId = rbac.currentUserId();

        CommunityMember target = community.member(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuário não é membro da comunidade: " + userId));

        if (target.getRole() == CommunityRole.OWNER) {
            throw new BusinessRuleException(
                    "O dono da comunidade não pode ser removido. Arquive a comunidade ou transfira a posse antes.");
        }

        boolean leavingSelf = currentUserId.equals(userId);
        if (!leavingSelf && !community.isAdmin(currentUserId)) {
            throw new UnauthorizedAccessException(
                    "Apenas OWNER/ADMIN podem remover outros membros da comunidade.");
        }

        community.getMembers().removeIf(m -> m.getUserId().equals(userId));
        community.setUpdatedAt(Instant.now());
        return toResponse(communityRepository.save(community));
    }

    // conversas da comunidade

    public ConversationResponseDTO startConversation(String id, CommunityConversationRequestDTO dto) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community);
        requireActive(community);

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
                            "Todos os participantes precisam ser membros da comunidade: " + participantId);
                }
                participants.add(participantId);
            }
        }

        if (participants.size() < 2) {
            throw new InvalidFieldException("Um grupo precisa de ao menos 2 participantes distintos.");
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
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada com id: " + id));
    }

    private void requireMember(ProjectCommunity community) {
        if (!community.isMember(rbac.currentUserId())) {
            throw new UnauthorizedAccessException("O usuário autenticado não participa desta comunidade.");
        }
    }

    private void requireAdmin(ProjectCommunity community) {
        if (!community.isAdmin(rbac.currentUserId())) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER ou ADMIN na comunidade.");
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
        List<CommunityMemberDTO> members = community.getMembers() == null ? List.of()
                : community.getMembers().stream()
                        .map(m -> CommunityMemberDTO.builder()
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
