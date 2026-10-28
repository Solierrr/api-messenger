package com.solaria.messenger.core.contract.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sucessor escolhido para um subgrupo ao remover um membro de comunidade
 */
public record SubgroupSuccessorRequestDTO(
        @NotBlank
        @Schema(example = "66fb99e8fa4ef1b28ad72fc1")
        String conversationId,
        @NotNull
        @jakarta.validation.constraints.Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                flags = jakarta.validation.constraints.Pattern.Flag.CASE_INSENSITIVE,
                message = "successorUserId deve ser um UUID")
        @Schema(format = "uuid", example = "00000000-0000-4000-8000-000000000002")
        String successorUserId) {

    /** Sucessor como UUID  -> formato inválido lança {@link IllegalArgumentException}. */
    public java.util.UUID successorUserIdValue() {
        return TransferManagerRequestDTO.parseCanonicalUuid(successorUserId);
    }
}
