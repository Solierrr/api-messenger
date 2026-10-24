package com.solaria.messenger.core.contract.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/**
 * Corpo opcional da remoção de um membro de comunidade
 */
public record CommunityMemberRemovalRequestDTO(
        @Valid
        @Schema(description = "Sucessores explícitos por subgrupo | grupos que ficarão vazios não exigem entrada")
        List<SubgroupSuccessorRequestDTO> successors) {
}
