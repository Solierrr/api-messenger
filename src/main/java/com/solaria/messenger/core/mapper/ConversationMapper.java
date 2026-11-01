package com.solaria.messenger.core.mapper;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.model.Conversation;

/** Mapeamento único da entidade persistida para o contrato público, sem consultas ou autorização */
public final class ConversationMapper {
    private ConversationMapper() { }

    /** Preserva os campos do contrato comum sem anexar preferências de destinatários */
    public static ConversationResponseDTO toResponse(Conversation conversation) {
        return ConversationResponseDTO.builder()
                .id(conversation.getId())
                .conversationType(conversation.getConversationType())
                .participantIds(conversation.getParticipantIds())
                .createdBy(conversation.getCreatedBy())
                .managerUserId(conversation.getManagerUserId())
                .title(conversation.getTitle())
                .communityId(conversation.getCommunityId())
                .userType(conversation.getUserType())
                .userDetails(conversation.getUserDetails())
                .startedAt(conversation.getStartedAt())
                .lastInteractionAt(conversation.getLastInteractionAt())
                .build();
    }

    /** Metadados de subgrupo listados pela comunidade */
    public static SubgroupMetadataResponseDTO toSubgroupMetadata(Conversation conversation) {
        return new SubgroupMetadataResponseDTO(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getParticipantIds(),
                conversation.getManagerUserId(),
                conversation.getStartedAt(),
                conversation.getLastInteractionAt());
    }
}
