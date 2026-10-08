package com.solaria.messenger.core.integration.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.solaria.messenger.core.integration.auth.dto.AuthLoginResponseDTO;
import com.solaria.messenger.core.integration.auth.dto.LoginRequestDTO;
import com.solaria.messenger.core.integration.auth.exception.AuthIntegrationException;

/**
 * Cliente HTTP do endpoint {@code POST /auth/login} da api-auth
 *
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #login(String, String)} -> autentica o usuário na api-auth e devolve os tokens</li>
 * </ul>
 */
// Cliente do endpoint POST /auth/login em api-auth
@Component
public class AuthClient {

    private final RestClient restClient;

    public AuthClient(RestClient authRestClient) {
        this.restClient = authRestClient;
    }

    /** Autentica na api-auth e devolve os tokens | falha HTTP vira {@link AuthIntegrationException} */
    public AuthLoginResponseDTO login(String email, String password) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword(password);
        try {
            return restClient.post()
                    .uri("/auth/login")
                    .body(request)
                    .retrieve()
                    .body(AuthLoginResponseDTO.class);
        } catch (RestClientResponseException e) {
            throw new AuthIntegrationException(
                    "Falha ao autenticar em api-auth: HTTP " + e.getStatusCode().value(), e);
        }
    }
}
