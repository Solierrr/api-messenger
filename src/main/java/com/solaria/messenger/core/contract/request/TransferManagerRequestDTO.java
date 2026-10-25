package com.solaria.messenger.core.contract.request;

import java.util.UUID;
import java.util.regex.Pattern;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern.Flag;

/**
 * DTO de requisição para transferência da gestão de um grupo ao novo gestor.
 *
 * <p>Usado no endpoint {@code PUT /messaging/conversations/{id}/manager}. Também expõe o parser de
 * UUID canônico reaproveitado por {@link SubgroupSuccessorRequestDTO}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #successorUserIdValue()} -> converte o sucessor informado em {@link UUID}.</li>
 *     <li>{@link #parseCanonicalUuid(String)} -> valida e converte um UUID canônico (8-4-4-4-12).</li>
 * </ul>
 */
public record TransferManagerRequestDTO(
        @NotBlank
        @jakarta.validation.constraints.Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                flags = Flag.CASE_INSENSITIVE,
                message = "successorUserId deve ser um UUID canônico")
        @Schema(format = "uuid", example = "00000000-0000-4000-8000-000000000002")
        String successorUserId) {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE);

    /** Sucessor como UUID -> formato inválido lança {@link IllegalArgumentException}. */
    public UUID successorUserIdValue() {
        return parseCanonicalUuid(successorUserId);
    }

    /** Aceita somente UUID canônico (8-4-4-4-12)*/
    public static UUID parseCanonicalUuid(String value) {
        if (value == null || !CANONICAL_UUID.matcher(value).matches()) {
            throw new IllegalArgumentException("successorUserId deve ser um UUID canônico");
        }
        return UUID.fromString(value);
    }
}
