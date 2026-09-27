package com.solaria.messenger.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.DirectConversationRequestDTO;
import com.solaria.messenger.dto.request.GroupConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.BusinessRuleException;
import com.solaria.messenger.exception.DuplicateResourceException;
import com.solaria.messenger.exception.InvalidFieldException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.ProjectCommunity;
import com.solaria.messenger.model.enums.CommunityStatus;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.repository.CommunityRepository;
import com.solaria.messenger.repository.ConversationRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;
    private final MongoTemplate mongoTemplate;

    public ConversationService(ConversationRepository conversationRepository,
            CommunityRepository communityRepository,
            RbacAuthorizationService rbac,
            MongoTemplate mongoTemplate) {
        this.conversationRepository = conversationRepository;
        this.communityRepository = communityRepository;
        this.rbac = rbac;
        this.mongoTemplate = mongoTemplate;
    }


    public ConversationResponseDTO createDirectConversation(DirectConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();
        UUID recipientId = dto.getRecipientId();

        if (currentUserId.equals(recipientId)) {
            throw new InvalidFieldException("Não é possível iniciar uma conversa consigo mesmo.");
        }

        Query query = Query.query(Criteria.where("conversationType").is(ConversationType.DIRECT)
                .and("status").is(ConversationStatus.ACTIVE)
                .and("participantIds").all(currentUserId, recipientId).size(2)).limit(2);
        List<Conversation> existing = mongoTemplate.find(query, Conversation.class);
        if (existing.size() > 1) {
            throw new DuplicateResourceException("Há múltiplas conversas diretas ativas para estes participantes.");
        }
        if (!existing.isEmpty()) {
            return toResponse(existing.getFirst());
        }

        Set<UUID> participants = new LinkedHashSet<>(List.of(currentUserId, recipientId));
        Conversation conversation = newConversation(ConversationType.DIRECT, currentUserId, participants);
        return toResponse(conversationRepository.save(conversation));
    }

    public ConversationResponseDTO createGroupConversation(GroupConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(currentUserId);
        dto.getParticipantIds().stream().filter(Objects::nonNull).forEach(participants::add);

        if (participants.size() < 2) {
            throw new InvalidFieldException("Um grupo precisa de ao menos 2 participantes distintos.");
        }

        Conversation conversation = newConversation(ConversationType.GROUP, currentUserId, participants);
        conversation.setTitle(dto.getTitle());

        return toResponse(conversationRepository.save(conversation));
    }

    public ConversationResponseDTO createChatbotConversation(ChatbotConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        Conversation conversation = newConversation(ConversationType.CHAT_BOT, currentUserId,
                new LinkedHashSet<>(List.of(currentUserId)));
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
            UUID creatorId,
            Set<UUID> participantIds) {
        Set<UUID> participants = new LinkedHashSet<>();
        participants.add(creatorId);
        participantIds.stream().filter(Objects::nonNull).forEach(participants::add);

        Conversation conversation = newConversation(ConversationType.GROUP, creatorId, participants);
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
        return conversationRepository
                .findByCommunityIdAndParticipantIdsContainingOrderByLastInteractionAtDesc(
                        communityId, rbac.currentUserId())
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
            if (community.getStatus() != CommunityStatus.ACTIVE) {
                throw new BusinessRuleException("A comunidade está arquivada.");
            }
            boolean allMembers = toAdd.stream().allMatch(community::isMember);
            if (!allMembers) {
                throw new BusinessRuleException(
                        "Todos os novos participantes precisam ser membros da comunidade.");
            }
        }

        Conversation updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(id)
                        .and("status").is(ConversationStatus.ACTIVE)
                        .and("conversationType").is(ConversationType.GROUP)),
                new Update().addToSet("participantIds").each(toAdd.toArray())
                        .max("lastInteractionAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Conversation.class);
        if (updated == null) {
            throw new BusinessRuleException("A conversa foi desativada por outra operação concorrente.");
        }
        return toResponse(updated);
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

        Conversation updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(id)),
                new Update().pull("participantIds", userId).max("lastInteractionAt", Instant.now()),
                FindAndModifyOptions.options().returnNew(true), Conversation.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Conversa não encontrada com id: " + id);
        }

        if (updated.getParticipantIds().isEmpty()) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("id").is(id).and("participantIds").size(0)),
                    Update.update("status", ConversationStatus.DEACTIVATED), Conversation.class);
            updated.setStatus(ConversationStatus.DEACTIVATED);
        }

        return toResponse(updated);
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
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(conversation.getId())),
                new Update().max("lastInteractionAt", timestamp), Conversation.class);
    }

    public int nextSequence(String conversationId) {
        Conversation updated = mongoTemplate.findAndModify(
                Query.query(Criteria.where("id").is(conversationId)),
                new Update().inc("lastSequence", 1),
                FindAndModifyOptions.options().returnNew(true), Conversation.class);
        if (updated == null) {
            throw new ResourceNotFoundException("Conversa não encontrada com id: " + conversationId);
        }
        return updated.getLastSequence();
    }

    private void requireGroup(Conversation conversation) {
        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new BusinessRuleException("Só conversas em grupo aceitam gestão de participantes.");
        }
    }

    private Conversation newConversation(ConversationType type, UUID createdBy, Set<UUID> participants) {
        Conversation conversation = new Conversation();
        conversation.setConversationType(type);
        conversation.setParticipantIds(participants);
        conversation.setCreatedBy(createdBy);
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
                .userType(conversation.getUserType())
                .userDetails(conversation.getUserDetails())
                .status(conversation.getStatus())
                .startedAt(conversation.getStartedAt())
                .lastInteractionAt(conversation.getLastInteractionAt())
                .build();
    }
}
