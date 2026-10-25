package com.solaria.messenger.rest.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.rest.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.rest.openapi.CommunityOpenApi;
import com.solaria.messenger.core.service.community.CommunityService;

import jakarta.validation.Valid;

/**
 * Controller REST dos recursos de comunidade, exposto em {@code /messaging/communities}.
 *
 * <p>Atua na camada de entrada HTTP: valida o corpo ({@code @Valid}) e delega a regra de negócio aos
 * serviço de comunidade. O contrato da API é declarado em
 * {@link CommunityOpenApi}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #createCommunity(CommunityRequestDTO)} -> cria a comunidade de um projeto (201).</li>
 *     <li>{@link #findMine()} -> lista as comunidades do usuário autenticado.</li>
 *     <li>{@link #findById(String)} -> busca uma comunidade da qual o usuário é membro.</li>
 *     <li>{@link #findByProjectId(UUID)} -> busca a comunidade de um projeto.</li>
 *     <li>{@link #updateCommunity(String, CommunityUpdateRequestDTO)} -> recusado: a situação vem do projeto.</li>
 *     <li>{@link #startConversation(String, CommunityConversationRequestDTO)} -> cria um subgrupo (201).</li>
 *     <li>{@link #listConversations(String)} -> lista os subgrupos visíveis ao usuário.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/communities")
public class CommunityController implements CommunityOpenApi {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /** Cria a comunidade do projeto e devolve 201 com a localização do recurso. */
    @Override
    @PostMapping
    public ResponseEntity<CommunityResponseDTO> createCommunity(@Valid @RequestBody CommunityRequestDTO dto) {
        CommunityResponseDTO response = communityService.createCommunity(dto);
        return ResponseEntity.created(URI.create("/messaging/communities/" + response.getId())).body(response);
    }

    /** Lista as comunidades do usuário autenticado. */
    @Override
    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponseDTO>> findMine() {
        return ResponseEntity.ok(communityService.findMine());
    }

    /** Busca uma comunidade da qual o usuário é membro. */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(communityService.findById(id));
    }

    /** Busca a comunidade de um projeto da qual o usuário é membro. */
    @Override
    @GetMapping("/project/{projectId}")
    public ResponseEntity<CommunityResponseDTO> findByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(communityService.findByProjectId(projectId));
    }

    /** Arquiva ou reativa a comunidade. */
    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<CommunityResponseDTO> updateCommunity(
            @PathVariable String id,
            @Valid @RequestBody CommunityUpdateRequestDTO dto) {
        return ResponseEntity.ok(communityService.updateCommunity(id, dto));
    }

    /** Cria um subgrupo entre membros da comunidade e devolve 201. */
    @Override
    @PostMapping("/{id}/conversations")
    public ResponseEntity<GroupCreationResponseDTO> startConversation(
            @PathVariable String id,
            @Valid @RequestBody CommunityConversationRequestDTO dto) {
        GroupCreationResponseDTO response = communityService.startConversation(id, dto.getTitle(), dto.getParticipantIds());
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.conversation().getId())).body(response);
    }

    /** Lista os subgrupos visíveis ao usuário na comunidade. */
    @Override
    @GetMapping("/{id}/conversations")
    public ResponseEntity<List<SubgroupMetadataResponseDTO>> listConversations(@PathVariable String id) {
        return ResponseEntity.ok(communityService.listConversations(id));
    }
}
