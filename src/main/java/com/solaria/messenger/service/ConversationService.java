package com.solaria.messenger.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.DirectConversationRequestDTO;
import com.solaria.messenger.dto.request.GroupConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.BusinessRuleException;
import com.solaria.messenger.exception.InvalidFieldException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.ProjectCommunity;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.repository.CommunityRepository;
import com.solaria.messenger.repository.ConversationRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;

    public ConversationService(ConversationRepository conversationRepository,
            CommunityRepository communityRepository,
            RbacAuthorizationService rbac) {
        this.conversationRepository = conversationRepository;
        this.communityRepository = communityRepository;
        this.rbac = rbac;
    }


    public ConversationResponseDTO createDirectConversation(DirectConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();
        UUID recipientId = dto.getRecipientId();

        if (currentUserId.equals(recipientId)) {
            throw new InvalidFieldException("Não é possível iniciar uma conversa consigo mesmo.");
        }

        Set<UUID> participants = new LinkedHashSet<>(List.of(currentUserId, recipientId));

        return findExistingDirect(currentUserId, participants)
                .map(this::toResponse)
                .orElseGet(() -> {
                    Conversation conversation = newConversation(ConversationType.DIRECT, currentUserId,
                            participants, dto.getEnvironment());
                    return toResponse(conversationRepository.save(conversation));
                });
    }

    public ConversationResponseDTO createGroupConversation(GroupConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(currentUserId);
        dto.getParticipantIds().stream().filter(Objects::nonNull).forEach(participants::add);

        if (participants.size() < 2) {
            throw new InvalidFieldException("Um grupo precisa de ao menos 2 participantes distintos.");
        }

        Conversation conversation = newConversation(ConversationType.GROUP, currentUserId,
                participants, dto.getEnvironment());
        conversation.setTitle(dto.getTitle());

        return toResponse(conversationRepository.save(conversation));
    }

    public ConversationResponseDTO createChatbotConversation(ChatbotConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        Conversation conversation = newConversation(ConversationType.CHAT_BOT, currentUserId,
                new LinkedHashSet<>(List.of(currentUserId)), dto.getEnvironment());
        conversation.setUserType(dto.getUserType());
        conversation.setUserDetails(dto.getUserDetails());

        return toResponse(conversationRepository.save(conversation));
    }

    /**
     * Cria uma conversa em grupo vinculada a uma comunidade
     * Chamado pelo {@code CommunityService}
     */
    public ConversationResponseDTO createCommunityGroupConversation(String communityId,
            String title,
            Environment environment,
            UUID creatorId,
            Set<UUID> participantIds) {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(creatorId);
        participantIds.stream().filter(Objects::nonNull).forEach(participants::add);

        Conversation conversation = newConversation(ConversationType.GROUP, creatorId, participants, environment);
        conversation.setTitle(title);
        conversation.setCommunityId(communityId);

        return toResponse(conversationRepository.save(conversation));
    }

    public ConversationResponseDTO findById(String id) {
        Conversation conversation = requireEntityById(id);
        requireParticipant(conversation);
        return toResponse(conversation);
    }

    public List<ConversationResponseDTO> findMine() {
        return conversationRepository
                .findByParticipantIdsContainingOrderByLastInteractionAtDesc(rbac.currentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ConversationResponseDTO> findByCommunity(String communityId) {
        return conversationRepository.findByCommunityIdOrderByLastInteractionAtDesc(communityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ConversationResponseDTO addParticipants(String id, Set<UUID> userIds) {
        Conversation conversation = requireEntityById(id);
        requireGroup(conversation);
        requireParticipant(conversation);
        requireActive(conversation);

        Set<UUID> toAdd = new LinkedHashSet<>();
        userIds.stream().filter(Objects::nonNull).forEach(toAdd::add);

        if (conversation.getCommunityId() != null) {
            ProjectCommunity community = communityRepository.findById(conversation.getCommunityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Comunidade não encontrada para a conversa: " + id));
            boolean allMembers = toAdd.stream().allMatch(community::isMember);
            if (!allMembers) {
                throw new BusinessRuleException(
                        "Todos os novos participantes precisam ser membros da comunidade.");
            }
        }

        conversation.getParticipantIds().addAll(toAdd);
        conversation.setLastInteractionAt(Instant.now());
        return toResponse(conversationRepository.save(conversation));
    }

    public ConversationResponseDTO removeParticipant(String id, UUID userId) {
        Conversation conversation = requireEntityById(id);
        requireGroup(conversation);
        requireParticipant(conversation);

        UUID currentUserId = rbac.currentUserId();
        boolean leavingSelf = currentUserId.equals(userId);
        boolean isGroupCreator = currentUserId.equals(conversation.getCreatedBy());
        if (!leavingSelf && !isGroupCreator) {
            throw new com.solaria.messenger.exception.UnauthorizedAccessException(
                    "Apenas quem criou o grupo pode remover outros participantes.");
        }

        conversation.getParticipantIds().remove(userId);
        if (conversation.getParticipantIds().isEmpty()) {
            conversation.setStatus(ConversationStatus.DEACTIVATED);
        }
        conversation.setLastInteractionAt(Instant.now());
        return toResponse(conversationRepository.save(conversation));
    }


    public Conversation requireEntityById(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada com id: " + id));
    }

    public void requireParticipant(Conversation conversation) {
        rbac.requireParticipant(conversation.getParticipantIds());
    }

    public void requireActive(Conversation conversation) {
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleException("A conversa está desativada e não aceita novas mensagens.");
        }
    }

    public void updateLastInteraction(Conversation conversation, Instant timestamp) {
        conversation.setLastInteractionAt(timestamp);
        conversationRepository.save(conversation);
    }

    private void requireGroup(Conversation conversation) {
        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new BusinessRuleException("Só conversas em grupo aceitam gestão de participantes.");
        }
    }

    private java.util.Optional<Conversation> findExistingDirect(UUID currentUserId, Set<UUID> participants) {
        return conversationRepository
                .findByConversationTypeAndParticipantIdsContaining(ConversationType.DIRECT, currentUserId)
                .stream()
                .filter(c -> c.getStatus() == ConversationStatus.ACTIVE)
                .filter(c -> participants.equals(c.getParticipantIds()))
                .findFirst();
    }

    private Conversation newConversation(ConversationType type, UUID createdBy,
            Set<UUID> participants, Environment environment) {
        Conversation conversation = new Conversation();
        conversation.setConversationType(type);
        conversation.setParticipantIds(participants);
        conversation.setCreatedBy(createdBy);
        conversation.setEnvironment(environment);
        conversation.setStatus(ConversationStatus.ACTIVE);

        Instant now = Instant.now();
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);
        return conversation;
    }

    private ConversationResponseDTO toResponse(Conversation conversation) {
        return ConversationResponseDTO.builder()
                .id(conversation.getId())
                .conversationType(conversation.getConversationType())
                .participantIds(conversation.getParticipantIds())
                .createdBy(conversation.getCreatedBy())
                .title(conversation.getTitle())
                .communityId(conversation.getCommunityId())
                .environment(conversation.getEnvironment())
                .userType(conversation.getUserType())
                .userDetails(conversation.getUserDetails())
                .status(conversation.getStatus())
                .startedAt(conversation.getStartedAt())
                .lastInteractionAt(conversation.getLastInteractionAt())
                .build();
    }
}
