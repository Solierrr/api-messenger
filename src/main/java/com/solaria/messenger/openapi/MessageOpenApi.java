package com.solaria.messenger.openapi;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Mensagens", description = "Mensagens trocadas por pessoas, entre si ou com o chatbot")
public interface MessageOpenApi {

    @Operation(
        summary = "Envia uma mensagem de uma pessoa numa conversa existente",
        description = "O messageType precisa combinar com o tipo da conversa: DIRECT -> USER_TO_USER, "
                + "GROUP -> USER_TO_GROUP, CHAT_BOT -> USER_TO_CHATBOT. CHATBOT_TO_USER é recusado. "
                + "O evento WS MESSAGE_CREATED correspondente (tópico /topic/conversations/{id}) é "
                + "best-effort: clientes conectados devem fazer resync via sinceSequence a cada "
                + "(re)conexão/SUBSCRIBE e deduplicar eventos pelo id da mensagem, em vez de assumir "
                + "entrega garantida ou exactly-once."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem enviada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos: messageType incompatível com a conversa ou CHATBOT_TO_USER"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada"),
            @ApiResponse(responseCode = "422", description = "A conversa está desativada")
    })
    ResponseEntity<MessageResponseDTO> sendMessage(MessageRequestDTO dto);

    @Operation(
        summary = "Lista as mensagens de uma conversa (paginado, ordem crescente)",
        description = "Lista até limit mensagens da conversa, em ordem crescente de sequence "
                + "(desempate por timestamp). Sem sinceSequence, começa do início da conversa; com "
                + "sinceSequence, retorna apenas mensagens com sequence maior que o valor informado. "
                + "Para sincronizar o histórico inteiro, o cliente pagina em loop usando o maior "
                + "sequence da página anterior como próximo sinceSequence, enquanto a página vier "
                + "cheia (limit itens)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de mensagens retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<List<MessageResponseDTO>> getMessagesByConversationId(String conversationId,
            @Parameter(description = "Retorna apenas mensagens com sequence maior que este valor, para sincronização incremental. Se omitido, começa do início da conversa.")
            Integer sinceSequence,
            @Parameter(description = "Tamanho máximo da página. Padrão 100, máximo 500.")
            Integer limit);
}
