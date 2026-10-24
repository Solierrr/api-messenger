package com.solaria.messenger.rest.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.rest.openapi.ConversationOpenApi;
import com.solaria.messenger.core.service.conversation.ConversationCreationService;
import com.solaria.messenger.core.service.conversation.ConversationQueryService;
import com.solaria.messenger.core.service.conversation.ConversationStatePatchService;
import com.solaria.messenger.core.service.conversation.GroupManagementService;

import jakarta.validation.Valid;


/**
 * Controller REST dos recursos de conversa, exposto em {@code /messaging/conversations}.
 *
 * <p>Atua na camada de entrada HTTP: valida o corpo ({@code @Valid}) e delega a regra de negócio aos
 * serviços de criação, consulta, estado pessoal e gestão de grupos. O contrato da API é declarado em
 * {@link ConversationOpenApi}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #createDirectConversation(DirectConversationRequestDTO)} -> cria ou reaproveita uma direta (201).</li>
 *     <li>{@link #createGroupConversation(GroupConversationRequestDTO)} -> cria um grupo (201).</li>
 *     <li>{@link #createChatbotConversation(ChatbotConversationRequestDTO)} -> cria uma conversa com o chatbot (201).</li>
 *     <li>{@link #findById(String)} -> busca uma conversa da qual o usuário participa.</li>
 *     <li>{@link #findMine(boolean)} >- lista as conversas do usuário com os cabeçalhos de sincronização.</li>
 *     <li>{@link #patchOwnedState(String, ConversationStatePatchRequestDTO)} -> altera leitura, mute ou arquivamento.</li>
 *     <li>{@link #addParticipants(String, ParticipantsRequestDTO)} -> adiciona participantes a um grupo.</li>
 *     <li>{@link #transferManager(String, TransferManagerRequestDTO)} -> transfere a gestão do grupo.</li>
 *     <li>{@link #removeParticipant(String, UUID, String)} -> remove um participante.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/conversations")
public class ConversationController implements ConversationOpenApi {

    private final ConversationCreationService creations;
    private final ConversationQueryService queries;
    private final ConversationStatePatchService statePatches;
    private final GroupManagementService groups;

    public ConversationController(ConversationCreationService creations, ConversationQueryService queries,
            ConversationStatePatchService statePatches, GroupManagementService groups) {
        this.creations = creations;
        this.queries = queries;
        this.statePatches = statePatches;
        this.groups = groups;
    }

    /** Cria ou reaproveita a direta com o destinatário e devolve 201. */
    @Override
    @PostMapping("/direct")
    public ResponseEntity<ConversationResponseDTO> createDirectConversation(
            @Valid @RequestBody DirectConversationRequestDTO dto) {
        ConversationResponseDTO response = creations.createDirectConversation(dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.getId())).body(response);
    }

    /** Cria um grupo com os convidados elegíveis e devolve 201 com os rejeitados. */
    @Override
    @PostMapping("/group")
    public ResponseEntity<GroupCreationResponseDTO> createGroupConversation(
            @Valid @RequestBody GroupConversationRequestDTO dto) {
        GroupCreationResponseDTO response = creations.createGroupConversation(dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.conversation().getId())).body(response);
    }

    /** Cria uma conversa com o chatbot e devolve 201. */
    @Override
    @PostMapping("/chatbot-conversations")
    public ResponseEntity<ConversationResponseDTO> createChatbotConversation(
            @Valid @RequestBody ChatbotConversationRequestDTO dto) {
        ConversationResponseDTO response = creations.createChatbotConversation(dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.getId())).body(response);
    }

    /** Busca uma conversa da qual o usuário participa. */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(queries.findById(id));
    }

    /** Lista as conversas do usuário com os cabeçalhos de sincronização (época e versão). */
    @Override
    @GetMapping("/me")
    public ResponseEntity<List<ConversationListItemResponseDTO>> findMine(
            @RequestParam(defaultValue = "false") boolean archived) {
        var snapshot = queries.findMineSnapshot(archived);
        return ResponseEntity.ok().header("X-Conversation-Sync-Epoch", snapshot.syncEpoch())
                .header("X-Conversation-Sync-Version", Long.toString(snapshot.syncVersion())).body(snapshot.items());
    }

    /** Altera cursor de leitura, mute ou archive do próprio usuário. */
    @Override
    @PatchMapping("/{id}/state")
    public ResponseEntity<ConversationStateResponseDTO> patchOwnedState(@PathVariable String id,
            @Valid @RequestBody(required = false) ConversationStatePatchRequestDTO patch) {
        return ResponseEntity.ok(statePatches.patchOwnedState(id, patch));
    }

    /** Adiciona participantes a um grupo gerido pelo usuário. */
    @Override
    @PostMapping("/{id}/participants")
    public ResponseEntity<ConversationResponseDTO> addParticipants(
            @PathVariable String id,
            @Valid @RequestBody ParticipantsRequestDTO dto) {
        return ResponseEntity.ok(groups.addParticipants(id, dto.getUserIds()));
    }

    /** Transfere a gestão do grupo para outro participante. */
    @Override
    @PutMapping("/{id}/manager")
    public ResponseEntity<ManagerTransferResponse> transferManager(
            @PathVariable String id,
            @Valid @RequestBody TransferManagerRequestDTO dto) {
        ConversationResponseDTO conversation = groups.transferManager(id, dto.successorUserIdValue());
        return ResponseEntity.ok(new ManagerTransferResponse(
                conversation.getId(), conversation.getManagerUserId()));
    }

    /** Remove um participante, ou o próprio usuário sai, validando o sucessor opcional como UUID canônico. */
    @Override
    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable String id,
            @PathVariable UUID userId,
            @RequestParam(required = false) String successorUserId) {
        UUID successor = null;
        if (successorUserId != null) {
            try {
                successor = TransferManagerRequestDTO.parseCanonicalUuid(successorUserId);
            } catch (IllegalArgumentException exception) {
                throw new InvalidFieldException(exception.getMessage());
            }
        }
        groups.removeParticipant(id, userId, successor);
        return ResponseEntity.noContent().build();
    }
}
