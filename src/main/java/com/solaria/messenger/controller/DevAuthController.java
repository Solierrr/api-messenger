package com.solaria.messenger.controller;

import com.solaria.messenger.dto.request.LoginRequestDTO;
import com.solaria.messenger.dto.response.AuthLoginResponseDTO;
import com.solaria.messenger.integration.auth.AuthClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Proxy de conveniência para autenticar direto pelo Swagger deste serviço,
// sem precisar acessar o Swagger de api-auth. Chama api-auth por baixo.
@RestController
@Tag(name = "Dev")
public class DevAuthController {

    private final AuthClient authClient;

    public DevAuthController(AuthClient authClient) {
        this.authClient = authClient;
    }

    @Operation(summary = "Autentica em api-auth e devolve o JWT, para colar no botão Authorize deste Swagger")
    @PostMapping("/dev/login")
    public AuthLoginResponseDTO login(@Valid @RequestBody LoginRequestDTO request) {
        return authClient.login(request.getEmail(), request.getPassword());
    }
}
