package com.solaria.messenger.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.solaria.messenger.model.enums.CommunityStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
