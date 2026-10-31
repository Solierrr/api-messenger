package com.solaria.messenger.rest.openapi;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.request.MessageEditRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;

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
                + "clientMessageId é opcional e serve somente para correlação. Cada envio cria outra mensagem e retorna 201, "
                + "mesmo com o mesmo clientMessageId. Não há replay nem retry automático. Conflitos comprovados de sequência "
                + "podem retornar 409; persistência sem confirmação retorna 503 MESSAGE_PERSISTENCE_UNCERTAIN. "
                + "O evento WS MESSAGE_CREATED correspondente (tópico /topic/users.{userId}.events de cada participante, inclusive o autor) é "
                + "best-effort: clientes conectados devem fazer resync via GET /messaging/updates a cada "
                + "(re)conexão e reconciliar ACK/broadcast pelo id persistido. Receber MESSAGE_CREATED não marca leitura."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem enviada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos: messageType incompatível com a conversa ou CHATBOT_TO_USER"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada"),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION ou DATA_INTEGRITY_VIOLATION; sem replay"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada ou tipo de mensagem incompatível"),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE antes do insert ou MESSAGE_PERSISTENCE_UNCERTAIN; não reenviar automaticamente")
    })
    ResponseEntity<MessageResponseDTO> sendMessage(MessageRequestDTO dto);

    @Operation(summary = "Edita o texto de uma mensagem própria",
            description = "Somente autor ativo e participante atual em DIRECT/GROUP. Texto igual é no-op; "
                    + "mensagem excluída e comunidade arquivada não permitem edição. Preserva chave e sequência.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado atualizado ou texto já atual"),
            @ApiResponse(responseCode = "400", description = "Texto inválido"),
            @ApiResponse(responseCode = "403", description = "Ator sem acesso ou autoria"),
            @ApiResponse(responseCode = "404", description = "Mensagem ou conversa inexistente"),
            @ApiResponse(responseCode = "409", description = "Mutação concorrente impediu confirmar a escrita"),
            @ApiResponse(responseCode = "422", description = "Estado da mensagem ou conversa impede edição, ou o prazo de edição (30 min por padrão) acabou"),
            @ApiResponse(responseCode = "503", description = "Não foi possível confirmar a mutação; consulte o estado atual antes de repetir")
    })
    ResponseEntity<MessageResponseDTO> editMessage(String id, MessageEditRequestDTO dto);

    @Operation(summary = "Exclui logicamente uma mensagem própria",
            description = "Remove o conteúdo e preserva identidade, chave e sequência. Repetição autorizada é no-op; "
                    + "comunidade arquivada não permite a operação, inclusive em repetição.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marcador de mensagem excluída"),
            @ApiResponse(responseCode = "403", description = "Ator sem acesso ou autoria"),
            @ApiResponse(responseCode = "404", description = "Mensagem ou conversa inexistente"),
            @ApiResponse(responseCode = "409", description = "Mutação concorrente impediu confirmar a escrita"),
            @ApiResponse(responseCode = "422", description = "Estado da conversa impede exclusão, ou o prazo de exclusão (30 min por padrão) acabou"),
            @ApiResponse(responseCode = "503", description = "Não foi possível confirmar a mutação; consulte o estado atual antes de repetir")
    })
    ResponseEntity<MessageResponseDTO> deleteMessage(String id);

    @Operation(
        summary = "Lista as mensagens de uma conversa (paginado, ordem crescente)",
        description = "Retorna um array em ordem crescente de sequence, com o estado atual de mensagens "
                + "editadas e excluídas. Sem cursor, seleciona as últimas limit mensagens; beforeSequence "
                + "seleciona as últimas limit anteriores ao cursor exclusivo; sinceSequence seleciona as "
                + "primeiras limit posteriores ao cursor exclusivo. Os cursores são mutuamente exclusivos. "
                + "Para recuperar backlog, avance pelo maior sequence retornado. Para detectar edições e "
                + "exclusões antigas ao reconectar, reconsulte o trecho carregado; sinceSequence após seu "
                + "máximo recupera apenas posições novas. A leitura autorizada permanece disponível em ARCHIVED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de mensagens retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Cursores inválidos ou informados simultaneamente"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<List<MessageResponseDTO>> getMessagesByConversationId(String conversationId,
            @Parameter(description = "Cursor exclusivo maior ou igual a zero: primeiras mensagens com sequence maior. Não combinar com beforeSequence.")
            Integer sinceSequence,
            @Parameter(description = "Cursor exclusivo maior ou igual a um: últimas mensagens com sequence menor. Não combinar com sinceSequence.")
            Integer beforeSequence,
            @Parameter(description = "Tamanho máximo da página. Ausente usa 100; valores fora de 1–500 são clampados.")
            Integer limit);
}
