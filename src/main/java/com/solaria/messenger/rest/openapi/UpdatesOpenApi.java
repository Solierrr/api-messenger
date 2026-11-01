package com.solaria.messenger.rest.openapi;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.core.contract.response.MessageUpdatesResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Mensagens", description = "Mensagens trocadas por pessoas, entre si ou com o chatbot")
public interface UpdatesOpenApi {
    @Operation(
        summary = "Devolve as mensagens que mudaram depois de uma versão (catch-up)",
        description = "Sem since, devolve só o ponto de partida. O cursor é a versão atual do relógio e messages vem vazio. "
                + "Com since, devolve o estado atual das mensagens com changeVersion maior que since, em ordem crescente "
                + "de changeVersion. A resposta traz mensagens novas, editadas e excluídas (tombstone) das conversas que "
                + "o usuário lê hoje e das que perdeu depois de since. Nas conversas perdidas, só entram as mensagens até "
                + "a versão da saída. Para a lista de conversas, use GET /messaging/conversations/me. O cursor é o maior "
                + "changeVersion devolvido, ou o since recebido quando não há mensagens. Com hasMore verdadeiro, repita a "
                + "chamada com since igual ao cursor. Se syncEpoch for diferente da época guardada, refaça a "
                + "sincronização completa. Para não perder eventos, assine /topic/users.{userId}.events antes de chamar."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de mudanças retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "INVALID_FIELD quando since é negativo. Um since não numérico também devolve 400")
    })
    ResponseEntity<MessageUpdatesResponseDTO> getUpdates(
            @Parameter(description = "Versão exclusiva, maior ou igual a zero. Devolve as mensagens com changeVersion maior "
                    + "que ela. Ausente devolve só o ponto de partida.")
            Long since,
            @Parameter(description = "Tamanho máximo da página. Ausente usa 100; valores fora de 1–500 são ajustados.")
            Integer limit);
}
