package com.solaria.messenger.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.model.enums.CommunityRole;

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
public class CommunityMemberDTO {

    private UUID userId;
    private CommunityRole role;
    private Instant joinedAt;
}
