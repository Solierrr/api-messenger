package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.solaria.messenger.core.model.enums.ConversationType;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com os dados de uma conversa.
 *
 * <p>Retornado pelos endpoints de criação e consulta de {@code /messaging/conversations}. Inclui
 * participantes, gestor, status e as datas de início e da última interação.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponseDTO {

    private String id;
    private ConversationType conversationType;
    private Set<UUID> participantIds;
    private UUID createdBy;
    private UUID managerUserId;
    private String title;
    private String communityId;
    private String userType;
    private Map<String, Object> userDetails;
    private Instant startedAt;
    private Instant lastInteractionAt;

    /** Convidados que ficaram de fora; só vem nas operações que recebem convidados. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<RejectedParticipantDTO> rejectedParticipants;
}
