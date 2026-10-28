package com.solaria.messenger.rest.controller.dev;

import com.solaria.messenger.core.integration.auth.dto.LoginRequestDTO;
import com.solaria.messenger.core.integration.auth.dto.AuthLoginResponseDTO;
import com.solaria.messenger.core.integration.auth.AuthClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de desenvolvimento que expõe {@code POST /dev/login}, proxy de autenticação para a api-auth.
 *
 * <p>Disponível somente no perfil {@code dev}. Permite obter um JWT pelo Swagger deste serviço, sem
 * acessar o Swagger da api-auth. A autenticação é delegada a {@link AuthClient}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #login(LoginRequestDTO)} - autentica em api-auth e devolve o JWT.</li>
 * </ul>
 */
// Proxy de conveniência para autenticar direto pelo Swagger deste serviço,
// sem precisar acessar o Swagger de api-auth. Chama api-auth por baixo.
@RestController
@Profile("dev")
@Tag(name = "Dev")
public class DevAuthController {

    private final AuthClient authClient;

    public DevAuthController(AuthClient authClient) {
        this.authClient = authClient;
    }

    /** Repassa o login à api-auth para obter um JWT pelo Swagger deste serviço. */
    @Operation(summary = "Autentica em api-auth e devolve o JWT, para colar no botão Authorize deste Swagger")
    @PostMapping("/dev/login")
    public AuthLoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authClient.login(request.getEmail(), request.getPassword());
    }
}
