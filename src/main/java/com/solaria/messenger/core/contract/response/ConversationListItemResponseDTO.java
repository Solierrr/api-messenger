package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;

/** Projeção própria compartilhada por REST e eventos; ordem local não é versão durável de domínio. */
public record ConversationListItemResponseDTO(
        String id,
        String syncEpoch,
        long syncVersion,
        ConversationType conversationType,
        Set<UUID> participantIds,
        UUID createdBy,
        UUID managerUserId,
        String title,
        String communityId,
        @JsonInclude(JsonInclude.Include.ALWAYS) CommunityStatus communityStatus,
        ConversationStatus status,
        Instant startedAt,
        Instant lastInteractionAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer lastReadSequence,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long unreadCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean muted,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean archived,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean effectiveMuted,
        @JsonInclude(JsonInclude.Include.ALWAYS) Preview lastMessage) {
}
