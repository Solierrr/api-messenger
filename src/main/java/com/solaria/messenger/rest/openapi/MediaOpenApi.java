package com.solaria.messenger.rest.openapi;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.core.contract.request.MediaUploadRequestDTO;
import com.solaria.messenger.core.contract.response.MediaListResponseDTO;
import com.solaria.messenger.core.contract.response.MediaUploadResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Contrato OpenAPI da transferência de mídia: o servidor só leva o arquivo de um aparelho a outro. */
@Tag(name = "Mídia", description = "Depósito e retirada temporária de arquivos de mensagens")
public interface MediaOpenApi {

    @Operation(
        summary = "Pede a permissão para depositar um arquivo",
        description = "Devolve o mediaId e o destino do envio. O cliente envia os bytes direto ao armazenamento com o método e "
                + "os cabeçalhos devolvidos (eles entram na assinatura), e só depois envia a mensagem citando o mediaId no "
                + "anexo. A permissão vale até uploadExpiresAt e pode ser repetida. O arquivo fica disponível por 7 dias."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Permissão emitida"),
            @ApiResponse(responseCode = "400", description = "INVALID_FIELD: tipo, mime ou tamanho fora do permitido"),
            @ApiResponse(responseCode = "403", description = "Quem não participa da conversa"),
            @ApiResponse(responseCode = "422", description = "BUSINESS_RULE_VIOLATION: conversa com o assistente ou comunidade arquivada"),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: armazenamento indisponível")
    })
    ResponseEntity<MediaUploadResponseDTO> createUpload(MediaUploadRequestDTO dto);

    @Operation(
        summary = "Retira um arquivo",
        description = "Confere a participação e responde 302 com o link temporário do armazenamento no cabeçalho Location "
                + "(vale poucos minutos e nasce nesta chamada). Não repasse o token ao seguir o redirecionamento. Depois do "
                + "prazo de 7 dias responde 410 MEDIA_EXPIRED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redireciona para o link temporário"),
            @ApiResponse(responseCode = "403", description = "Quem não participa mais da conversa"),
            @ApiResponse(responseCode = "410", description = "MEDIA_EXPIRED: o arquivo passou do prazo ou nunca existiu"),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE: armazenamento indisponível")
    })
    ResponseEntity<Void> getContent(@Parameter(description = "Identificador do arquivo, do campo mediaId do anexo") String mediaId);

    @Operation(
        summary = "Lista as mídias ainda disponíveis",
        description = "Para quem acabou de iniciar sessão: mídias não expiradas e não excluídas das conversas que o usuário "
                + "lê, da mais recente para a mais antiga. Com conversationId, só dessa conversa. A localização não aparece, "
                + "porque não tem arquivo. Com nextCursor, repita a chamada com cursor igual a ele. O mesmo dado chega ao "
                + "vivo no evento MESSAGE_CREATED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de mídias"),
            @ApiResponse(responseCode = "400", description = "INVALID_FIELD: cursor inválido"),
            @ApiResponse(responseCode = "403", description = "Quem não participa da conversa pedida")
    })
    ResponseEntity<MediaListResponseDTO> listMedia(
            @Parameter(description = "Limita a uma conversa. Ausente lista todas as que o usuário lê.") String conversationId,
            @Parameter(description = "Tamanho da página. Ausente usa 50; valores fora de 1–100 são ajustados.") Integer limit,
            @Parameter(description = "nextCursor da página anterior; opaco.") String cursor);
}
