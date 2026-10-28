package com.solaria.messenger.core.integration.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requisição enviado à api-auth no login ({@code POST /auth/login})
 *
 */
@Setter
@Getter
public class LoginRequestDTO {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

}
