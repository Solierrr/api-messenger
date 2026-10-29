package com.solaria.messenger.rest.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import com.solaria.messenger.core.contract.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.core.contract.request.ConversationStatePatchRequestDTO;
import com.solaria.messenger.core.contract.request.DirectConversationRequestDTO;
import com.solaria.messenger.core.contract.request.GroupConversationRequestDTO;
import com.solaria.messenger.rest.dto.request.ParticipantsRequestDTO;
import com.solaria.messenger.core.contract.request.TransferManagerRequestDTO;
import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.contract.response.ConversationStateResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.rest.dto.response.ManagerTransferResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Conversas", description = "Conversas diretas, em grupo e com o chatbot")
public interface ConversationOpenApi {
    @Operation(
        summary = "Inicia uma conversa direta entre duas pessoas",
        description = "O iniciador é o usuário autenticado. O par canônico identifica uma única DIRECT, ativa ou "
                + "desativada. Após revalidar atividade e autorização, reutiliza ou reativa o mesmo ID, histórico e "
                + "preferências. Criação, reuso e reativação retornam 201 com Location. Não há retry automático."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa criada ou reaproveitada", headers = {
                    @Header(name = "Location", description = "URI da conversa criada ou reaproveitada",
                            schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, incluindo conversa consigo mesmo",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "O ator ou destinatário está inativo na Auth",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "O destinatário não está cadastrado na Auth",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "DIRECT_IDENTITY_INCONSISTENT ou CONCURRENT_MODIFICATION",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Estado pessoal ou baseline existente inconsistente; sem reparação automática",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Dependência indisponível ou persistência/reativação sem resultado confirmado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ConversationResponseDTO> createDirectConversation(DirectConversationRequestDTO dto);

    @Operation(summary = "Inicia uma conversa em grupo",
            description = "Inclui o criador e exige ao menos dois participantes distintos elegíveis. "
                    + "Inelegibilidade individual exclui apenas o convidado; a resposta informa seu ID e motivo genérico. "
                    + "Auth e autorização são validadas antes da transação; conversa e estados pessoais são atômicos. "
                    + "Falha técnica aborta a criação inteira, sem retry automático ou idempotência do POST.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Grupo criado, participantes admitidos e rejeições genéricas",
                    headers = @Header(name = "Location", description = "URI da conversa criada"),
                    content = @Content(schema = @Schema(implementation = GroupCreationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Criador sem autorização ou inativo"),
            @ApiResponse(responseCode = "422", description = "Menos de dois participantes distintos elegíveis"),
            @ApiResponse(responseCode = "503", description = "Auth/banco indisponível ou resultado da transação não confirmado")
    })
    ResponseEntity<GroupCreationResponseDTO> createGroupConversation(GroupConversationRequestDTO dto);

    @Operation(
        summary = "Inicia uma conversa com o chatbot",
        description = " o inciador da conversa é o id do JWT do usuario autenticado"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<ConversationResponseDTO> createChatbotConversation(ChatbotConversationRequestDTO dto);

    @Operation(
        summary = "Busca uma conversa pelo identificador",
        description = "Só participantes da conversa podem acessá-la"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversa encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<ConversationResponseDTO> findById(String id);

    @Operation(summary = "Lista conversas autorizadas com estado próprio",
            description = "archived=false seleciona a principal; true seleciona arquivadas pessoais. "
                    + "Valida baseline de todos os humanos autorizados antes do filtro. Não inicializa estado. "
                    + "CHAT_BOT fica só na principal e omite as cinco chaves pessoais; preserva nulls de preview. "
                    + "Prévia/badge vêm de mensagens persistidas em lote. Ordem por última interação decrescente, id crescente. "
                    + "A mesma projeção é publicada em CONVERSATION_UPDATED em /topic/users.{userId}.events. "
                    + "syncEpoch/syncVersion ordenam itens; headers marcam o snapshot inteiro, inclusive vazio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Array de itens próprios", headers = {
                    @Header(name = "X-Conversation-Sync-Epoch", description = "Identidade da ordem local desta JVM",
                            schema = @Schema(type = "string", format = "uuid")),
                    @Header(name = "X-Conversation-Sync-Version", description = "Fronteira do snapshot para reconciliar ausências",
                            schema = @Schema(type = "integer", format = "int64"))
            }),
            @ApiResponse(responseCode = "403", description = "UNAUTHORIZED_ACCESS; atividade/participação exigidas"),
            @ApiResponse(responseCode = "422", description = "BUSINESS_RULE_VIOLATION; baseline de admissão não verificável, sem resposta parcial")
    })
    ResponseEntity<List<ConversationListItemResponseDTO>> findMine(boolean archived);

    @Operation(summary = "Altera leitura e preferências do próprio ator",
            description = "PATCH parcial, ao menos uma chave presente e não nula. Cursor monotônico até head persistido. "
                    + "Valor menor não impede outra preferência válida. No-op não grava/publica. "
                    + "Comunidade ARCHIVED permite estado pessoal. Baseline vem da admissão, nunca do pedido. "
                    + "Resposta inclui syncEpoch/syncVersion para reconciliar com o evento privado completo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado próprio atual"),
            @ApiResponse(responseCode = "400", description = "INVALID_FIELD; vazio/nulo/tipos/cursor futuro, sem efeitos parciais"),
            @ApiResponse(responseCode = "403", description = "UNAUTHORIZED_ACCESS"),
            @ApiResponse(responseCode = "404", description = "Conversa inexistente"),
            @ApiResponse(responseCode = "422", description = "BUSINESS_RULE_VIOLATION; CHAT_BOT ou baseline não verificável")
    })
    ResponseEntity<ConversationStateResponseDTO> patchOwnedState(String id, ConversationStatePatchRequestDTO patch);

    @Operation(
        summary = "Adiciona participantes a uma conversa em grupo",
        description = "Somente o gestor atual adiciona participantes. Grupo e comunidade devem estar ativos; "
                + "em subgrupo, os convidados precisam pertencer à comunidade e estar ativos na Auth. "
                + "Participação e estados são gravados atomicamente; primeiro baseline é o head da entrada. "
                + "Readmissão preserva baseline original, leitura e preferências, sem scanner ou reparação automática."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participantes adicionados"),
            @ApiResponse(responseCode = "403", description = "Papel insuficiente ou convidado inativo"),
            @ApiResponse(responseCode = "404", description = "Conversa ou novo participante não encontrado"),
            @ApiResponse(responseCode = "409", description = "GROUP_MANAGER_UNAVAILABLE ou CONCURRENT_MODIFICATION"),
            @ApiResponse(responseCode = "422", description = "Grupo/comunidade inativos, participante fora da comunidade ou estado inconsistente"),
            @ApiResponse(responseCode = "503", description = "Auth/banco indisponível ou resultado da admissão não confirmado")
    })
    ResponseEntity<ConversationResponseDTO> addParticipants(String id, ParticipantsRequestDTO dto);

    @Operation(
        summary = "Transfere a gestão atual do grupo",
        description = "O gestor atual designa outro participante ativo; grupo e comunidade devem estar ativos. "
                + "Não há replay para o antigo gestor. A resposta contém conversationId e managerUserId."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gestão transferida"),
            @ApiResponse(responseCode = "400", description = "UUID canônico inválido"),
            @ApiResponse(responseCode = "403", description = "Sem autoridade para transferir"),
            @ApiResponse(responseCode = "404", description = "Conversa ou sucessor não encontrado"),
            @ApiResponse(responseCode = "422", description = "Grupo ou comunidade arquivados/desativados"),
            @ApiResponse(responseCode = "409", description = "SUCCESSOR_REQUIRED, SUCCESSOR_INELIGIBLE, GROUP_MANAGER_UNAVAILABLE ou CONCURRENT_MODIFICATION")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = TransferManagerRequestDTO.class)))
    ResponseEntity<ManagerTransferResponse> transferManager(
            String id, TransferManagerRequestDTO dto);

    @Operation(
        summary = "Remove um participante de um grupo ou sai do grupo",
        description = "Somente o gestor pode remover terceiros, com grupo e comunidade ativos. "
                + "Saída voluntária continua permitida no archive, com sucessor quando o gestor sai. "
                + "Retorna 204 sem roster; repetir a retirada dos próprios resíduos não concede acesso."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Participação removida; sem corpo"),
            @ApiResponse(responseCode = "400", description = "UUID canônico inválido"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para remover esse participante"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada"),
            @ApiResponse(responseCode = "422", description = "Administração ordinária bloqueada por archive ou desativação"),
            @ApiResponse(responseCode = "409", description = "SUCCESSOR_REQUIRED, SUCCESSOR_INELIGIBLE, GROUP_MANAGER_UNAVAILABLE ou CONCURRENT_MODIFICATION"),
            @ApiResponse(responseCode = "503", description = "DEPENDENCY_UNAVAILABLE; falha na consulta de atividade")
    })
    ResponseEntity<Void> removeParticipant(
            String id,
            UUID userId,
            @Parameter(name = "successorUserId", in = ParameterIn.QUERY,
                    schema = @Schema(type = "string", format = "uuid",
                            pattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
            String successorUserId);

    default ResponseEntity<Void> removeParticipant(String id, UUID userId) {
        return removeParticipant(id, userId, null);
    }
}
