package com.solaria.messenger.dto.request;

import com.solaria.messenger.model.enums.CommunityStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * Atualiza o status da comunidade
 */
@Getter
@Setter
public class CommunityUpdateRequestDTO {

    private CommunityStatus status;
}
