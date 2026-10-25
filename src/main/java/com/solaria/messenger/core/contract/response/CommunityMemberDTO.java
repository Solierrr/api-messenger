package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.core.model.enums.CommunityRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com um membro de comunidade: usuário, papel e data de entrada.
 *
 * <p>Compõe {@link CommunityResponseDTO}.</p>
 */
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
