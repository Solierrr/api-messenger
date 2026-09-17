package com.solaria.messenger.controller;

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

import com.solaria.messenger.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.dto.request.CommunityMembersRequestDTO;
import com.solaria.messenger.dto.request.CommunityRequestDTO;
import com.solaria.messenger.dto.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.dto.response.CommunityResponseDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.openapi.CommunityOpenApi;
import com.solaria.messenger.service.CommunityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/messaging/communities")
public class CommunityController implements CommunityOpenApi {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Override
    @PostMapping
    public ResponseEntity<CommunityResponseDTO> createCommunity(@Valid @RequestBody CommunityRequestDTO dto) {
        CommunityResponseDTO response = communityService.createCommunity(dto);
        return ResponseEntity.created(URI.create("/messaging/communities/" + response.getId())).body(response);
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<List<CommunityResponseDTO>> findMine() {
        return ResponseEntity.ok(communityService.findMine());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(communityService.findById(id));
    }

    @Override
    @GetMapping("/project/{projectId}")
    public ResponseEntity<CommunityResponseDTO> findByProjectId(@PathVariable UUID projectId) {
        return ResponseEntity.ok(communityService.findByProjectId(projectId));
    }

    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<CommunityResponseDTO> updateCommunity(
            @PathVariable String id,
            @Valid @RequestBody CommunityUpdateRequestDTO dto) {
        return ResponseEntity.ok(communityService.updateCommunity(id, dto));
    }

    @Override
    @PostMapping("/{id}/members")
    public ResponseEntity<CommunityResponseDTO> addMembers(
            @PathVariable String id,
            @Valid @RequestBody CommunityMembersRequestDTO dto) {
        return ResponseEntity.ok(communityService.addMembers(id, dto));
    }

    @Override
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<CommunityResponseDTO> removeMember(
            @PathVariable String id,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(communityService.removeMember(id, userId));
    }

    @Override
    @PostMapping("/{id}/conversations")
    public ResponseEntity<ConversationResponseDTO> startConversation(
            @PathVariable String id,
            @Valid @RequestBody CommunityConversationRequestDTO dto) {
        ConversationResponseDTO response = communityService.startConversation(id, dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.getId())).body(response);
    }

    @Override
    @GetMapping("/{id}/conversations")
    public ResponseEntity<List<ConversationResponseDTO>> listConversations(@PathVariable String id) {
        return ResponseEntity.ok(communityService.listConversations(id));
    }
}
