package com.solaria.messenger.rest.openapi;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.core.contract.request.NpsRequestDTO;
import com.solaria.messenger.core.contract.response.NpsResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Contrato Swagger/OpenAPI do NPS, em {@code /messaging/nps}.
 *
 * <p>Declara a operação de registro e seus códigos de resposta. A implementação fica em
 * {@link com.solaria.messenger.rest.controller.NpsController}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #create(NpsRequestDTO)} - registra a nota NPS de uma conversa com o chatbot.</li>
 * </ul>
 */
@Tag(name = "NPS", description = "Nota de satisfação (0 a 10) das conversas com o chatbot")
public interface NpsOpenApi {

    /** Contrato OpenAPI: registra a nota NPS de uma conversa com o chatbot. */
    @Operation(
        summary = "Registra a nota NPS de uma conversa com o chatbot",
        description = "O usuário é sempre o autenticado da requisição e precisa participar da conversa. "
                + "Classificação: 0-6 detrator, 7-8 neutro, 9-10 promotor. Uma resposta por conversa."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "NPS registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não participa da conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conversa já avaliada"),
            @ApiResponse(responseCode = "422", description = "A conversa não é uma conversa com o chatbot")
    })
    ResponseEntity<NpsResponseDTO> create(NpsRequestDTO dto);
}
