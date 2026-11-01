package com.solaria.messenger.rest.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.rest.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityMembersRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityMemberRemovalRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Comunidades", description = "Comunidade do projeto, espaço tipo slack")
public interface CommunityOpenApi {
    @Operation(
        summary = "Cria a comunidade de um projeto",
        description = "Quem cria vira OWNER | memberIds opcionais entram como MEMBER | Uma comunidade por projeto. "
        + "Só o demandante do projeto pode criar | memberIds inexistentes ou inativos ficam de fora e voltam em "
        + "rejectedParticipants; se nenhum passar, responde 422"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comunidade criada"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não é o demandante"),
            @ApiResponse(responseCode = "404", description = "Projeto não encontrado"),
            @ApiResponse(responseCode = "409", description = "Já existe uma comunidade para o projeto"),
            @ApiResponse(responseCode = "503", description = "Dependência indisponível para validar o demandante ou os membros")
    })
    ResponseEntity<CommunityResponseDTO> createCommunity(CommunityRequestDTO dto);

    @Operation(summary = "Lista as comunidades das quais o usuário autenticado é membro")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"))
    ResponseEntity<List<CommunityResponseDTO>> findMine();

    @Operation(summary = "Busca uma comunidade pelo identificador (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<CommunityResponseDTO> findById(String id);

    @Operation(summary = "Busca a comunidade de um projeto (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada para o projeto")
    })
    ResponseEntity<CommunityResponseDTO> findByProjectId(UUID projectId);

    @Operation(
        summary = "Altera somente o status da comunidade",
        description = "status é obrigatório; pedido vazio ou status nulo é 400 INVALID_FIELD, sem efeitos. "
                + "Arquivar e reativar exigem OWNER/ADMIN atual. Mesmo status é no-op autorizado. "
                + "ARCHIVED bloqueia mensagens e administração ordinária, preservando leitura, estado pessoal e saída. "
                + "Não há cleanup de typing nem metadados descritivos editáveis."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado ou no-op autorizado"),
            @ApiResponse(responseCode = "400", description = "INVALID_FIELD; status obrigatório, não nulo e válido"),
            @ApiResponse(responseCode = "403", description = "Arquivar e ativar exigem OWNER/ADMIN"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION"),
            @ApiResponse(responseCode = "503", description = "Falha técnica de persistência ou de dependência")
    })
    ResponseEntity<CommunityResponseDTO> updateCommunity(String id, CommunityUpdateRequestDTO dto);

    @Operation(
        summary = "Adiciona/altera papel de membros",
        description = "Adicionar e conceder ADMIN exigem ADMIN | rebaixar um ADMIN exige OWNER | novos membros "
                + "inexistentes ou inativos ficam de fora e voltam em rejectedParticipants; se nenhum passar, 422"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membros adicionados"),
            @ApiResponse(responseCode = "403", description = "Papel insuficiente"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION ou MEMBERSHIP_CLEANUP_REQUIRED antes da readmissão"),
            @ApiResponse(responseCode = "503", description = "Core indisponível para validar novos membros"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada ou papel OWNER solicitado")
    })
    ResponseEntity<CommunityResponseDTO> addMembers(String id, CommunityMembersRequestDTO dto);

    @Operation(
        summary = "Remove um membro | sair da comunidade",
        description = "Cada um pode sair | remover outros exige OWNER/ADMIN | remover ADMIN exige OWNER | "
                + "OWNER não pode ser removido nem sair. "
                + "A remoção também revoga participação nos subgrupos. Informe sucessor por conversa quando necessário; "
                + "Grupo que fica vazio é apagado com mensagens e estados. Retorna 204 sem roster. "
                + "Após revogar no pai, repetição explícita só limpa resíduos; readmissão aguarda essa limpeza. "
                + "Saída voluntária é permitida em comunidade arquivada; remoção ordinária de terceiros é bloqueada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Membro e participações residuais removidos; sem corpo"),
            @ApiResponse(responseCode = "400", description = "Sucessor duplicado/alheio, UUID ou corpo inválido"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para remover esse membro"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "409", description = "SUCCESSOR_REQUIRED, SUCCESSOR_INELIGIBLE, GROUP_MANAGER_UNAVAILABLE ou CONCURRENT_MODIFICATION"),
            @ApiResponse(responseCode = "422", description = "Tentativa de remover o OWNER ou administrar terceiros em comunidade arquivada"),
            @ApiResponse(responseCode = "503", description = "REMOVAL_INCOMPLETE; repetir DELETE revalida o estado atual e conclui a limpeza")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @Content(schema = @Schema(implementation = CommunityMemberRemovalRequestDTO.class)))
    ResponseEntity<Void> removeMember(
            String id, UUID userId, CommunityMemberRemovalRequestDTO dto);

    default ResponseEntity<Void> removeMember(String id, UUID userId) {
        return removeMember(id, userId, null);
    }

    @Operation(
        summary = "Inicia uma conversa em grupo entre membros da comunidade",
        description = "participantIds omitido ou vazio seleciona os membros atuais; se informado, seleciona os IDs pedidos. "
                + "O criador autorizado entra sempre, com ao menos outro participante elegível. "
                + "Convidados inexistentes, inativos ou fora da comunidade ficam de fora e voltam com o motivo. "
                + "Falhas técnicas abortam o comando; conversa e estados pessoais são atômicos, sem retry de criação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subgrupo criado, participantes admitidos e rejeições genéricas",
                    content = @Content(schema = @Schema(implementation = GroupCreationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Criador não participa da comunidade ou está inativo"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada ou menos de dois participantes distintos elegíveis"),
            @ApiResponse(responseCode = "503", description = "Auth/banco indisponível ou resultado da transação não confirmado")
    })
    ResponseEntity<GroupCreationResponseDTO> startConversation(String id, CommunityConversationRequestDTO dto);

    @Operation(summary = "Lista metadados dos subgrupos da comunidade",
            description = "OWNER/ADMIN atuais veem todos os GROUPs; MEMBER vê apenas os GROUPs de que participa. "
                    + "Disponível em ARCHIVED, com sete campos e nulos explícitos; não concede acesso ao conteúdo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<List<SubgroupMetadataResponseDTO>> listConversations(String id);
}
