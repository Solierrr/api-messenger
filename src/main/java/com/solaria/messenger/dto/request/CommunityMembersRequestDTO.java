package com.solaria.messenger.dto.request;

import java.util.Set;
import java.util.UUID;

import com.solaria.messenger.model.enums.CommunityRole;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Adiciona membros numa comunidade
 * {@code role} default = {@code MEMBER}
 * Conceder {@code ADMIN} exige que o chamador seja {@code OWNER};
 * {@code OWNER} não pode ser atribuído por aqui
 */
@Getter
@Setter
public class CommunityMembersRequestDTO {

    @NotEmpty(message = "Informe ao menos um usuário")
    private Set<@NotNull(message = "userId inválido") UUID> userIds;

    private CommunityRole role;
}
