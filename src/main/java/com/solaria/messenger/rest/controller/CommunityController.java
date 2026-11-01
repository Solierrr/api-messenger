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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.rest.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityMembersRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityMemberRemovalRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.rest.openapi.CommunityOpenApi;
import com.solaria.messenger.core.usecase.community.AddCommunityMembersUseCase;
import com.solaria.messenger.core.usecase.community.CreateCommunityUseCase;
import com.solaria.messenger.core.usecase.community.GetCommunityByProjectUseCase;
import com.solaria.messenger.core.usecase.community.GetCommunityUseCase;
import com.solaria.messenger.core.usecase.community.ListCommunitySubgroupsUseCase;
import com.solaria.messenger.core.usecase.community.ListMyCommunitiesUseCase;
import com.solaria.messenger.core.usecase.community.RemoveCommunityMemberUseCase;
import com.solaria.messenger.core.usecase.community.UpdateCommunityStatusUseCase;
import com.solaria.messenger.core.usecase.conversation.CreateGroupConversationUseCase;

import jakarta.validation.Valid;

/**
 * Controller REST dos recursos de comunidade, exposto em {@code /messaging/communities}.
 *
 * <p>
 * Atua na camada de entrada HTTP: valida o corpo ({@code @Valid}) e delega a regra de negócio aos
 * serviços de comunidade, de membros e de criação de conversas. O contrato da API é declarado em
 * {@link CommunityOpenApi}.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #createCommunity(CommunityRequestDTO)} -> cria a comunidade de um projeto (201).</li>
 *     <li>{@link #findMine()} -> lista as comunidades do usuário autenticado.</li>
 *     <li>{@link #findById(String)} -> busca uma comunidade da qual o usuário é membro.</li>
 *     <li>{@link #findByProjectId(UUID)} -> busca a comunidade de um projeto.</li>
 *     <li>{@link #updateCommunity(String, CommunityUpdateRequestDTO)} -> arquiva ou reativa a comunidade.</li>
 *     <li>{@link #addMembers(String, CommunityMembersRequestDTO)} -> adiciona membros ou altera papéis.</li>
 *     <li>{@link #removeMember(String, UUID, CommunityMemberRemovalRequestDTO)} -> remove um membro.</li>
 *     <li>{@link #startConversation(String, CommunityConversationRequestDTO)} -> cria um subgrupo (201).</li>
 *     <li>{@link #listConversations(String)} -> lista os subgrupos visíveis ao usuário.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/communities")
public class CommunityController implements CommunityOpenApi {

    private final CreateCommunityUseCase createCommunity;
    private final ListMyCommunitiesUseCase listMine;
    private final GetCommunityUseCase getCommunity;
    private final GetCommunityByProjectUseCase getByProject;
    private final UpdateCommunityStatusUseCase updateStatus;
    private final AddCommunityMembersUseCase addMembers;
    private final RemoveCommunityMemberUseCase removeMember;
    private final CreateGroupConversationUseCase createGroup;
    private final ListCommunitySubgroupsUseCase listSubgroups;

    public CommunityController(CreateCommunityUseCase createCommunity, ListMyCommunitiesUseCase listMine,
            GetCommunityUseCase getCommunity, GetCommunityByProjectUseCase getByProject,
            UpdateCommunityStatusUseCase updateStatus, AddCommunityMembersUseCase addMembers,
            RemoveCommunityMemberUseCase removeMember, CreateGroupConversationUseCase createGroup,
            ListCommunitySubgroupsUseCase listSubgroups) {
        this.createCommunity = createCommunity;
        this.listMine = listMine;
        this.getCommunity = getCommunity;
        this.getByProject = getByProject;
        this.updateStatus = updateStatus;
        this.addMembers = addMembers;
        this.removeMember = removeMember;
        this.createGroup = createGroup;
        this.listSubgroups = listSubgroups;
    }

    /** Cria a comunidade do projeto e devolve 201 com a localização do recurso. */
    @Override
    @PostMapping
    public ResponseEntity<CommunityResponseDTO> createCommunity(@Valid @RequestBody CommunityRequestDTO dto) {
        CommunityResponseDTO response = createCommunity.execute(dto);
        return ResponseEntity.created(URI.create("/messaging/communities/" + response.getId())).body(response);
    }

    /** Lista as comunidades do usuário autenticado. */
    @Override
    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponseDTO>> findMine() {
        return ResponseEntity.ok(listMine.execute());
    }

    /** Busca uma comunidade da qual o usuário é membro. */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(getCommunity.execute(id));
    }

    /** Busca a comunidade de um projeto da qual o usuário é membro. */
    @Override
    @GetMapping("/project/{projectId}")
    public ResponseEntity<CommunityResponseDTO> findByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(getByProject.execute(projectId));
    }

    /** Arquiva ou reativa a comunidade. */
    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<CommunityResponseDTO> updateCommunity(
            @PathVariable String id,
            @Valid @RequestBody CommunityUpdateRequestDTO dto) {
        return ResponseEntity.ok(updateStatus.execute(id, dto));
    }

    /** Adiciona membros ou altera o papel de membros existentes. */
    @Override
    @PostMapping("/{id}/members")
    public ResponseEntity<CommunityResponseDTO> addMembers(
            @PathVariable String id,
            @Valid @RequestBody CommunityMembersRequestDTO dto) {
        return ResponseEntity.ok(addMembers.execute(id, dto));
    }

    /** Remove um membro, ou o próprio usuário sai, com os sucessores opcionais dos subgrupos. */
    @Override
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String id,
            @PathVariable UUID userId,
            @Valid @RequestBody(required = false) CommunityMemberRemovalRequestDTO dto) {
        removeMember.execute(id, userId, dto);
        return ResponseEntity.noContent().build();
    }

    /** Cria um subgrupo entre membros da comunidade e devolve 201. */
    @Override
    @PostMapping("/{id}/conversations")
    public ResponseEntity<GroupCreationResponseDTO> startConversation(
            @PathVariable String id,
            @Valid @RequestBody CommunityConversationRequestDTO dto) {
        GroupCreationResponseDTO response = createGroup.execute(id, dto.getTitle(), dto.getParticipantIds());
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.conversation().getId())).body(response);
    }

    /** Lista os subgrupos visíveis ao usuário na comunidade. */
    @Override
    @GetMapping("/{id}/conversations")
    public ResponseEntity<List<SubgroupMetadataResponseDTO>> listConversations(@PathVariable String id) {
        return ResponseEntity.ok(listSubgroups.execute(id));
    }
}
