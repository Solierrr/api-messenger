package com.solaria.messenger.core.integration.auth.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO que espelha a resposta do endpoint {@code POST /auth/login} da api-auth
 */
@Setter
@Getter
public class AuthLoginResponseDTO {

    private String accessToken;

    private String refreshToken;

    private String accessTokenExpiresAt;

    private String userId;

    private String email;

}
