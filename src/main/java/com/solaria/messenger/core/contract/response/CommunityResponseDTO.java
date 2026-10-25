package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.solaria.messenger.core.model.enums.CommunityStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com os dados de uma comunidade e de seus membros.
 *
 * <p>Retornado pelos endpoints de {@code /messaging/communities}.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityResponseDTO {

    private String id;
    private UUID projectId;
    private List<CommunityMemberDTO> members;
    private CommunityStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
