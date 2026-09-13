package com.solaria.messenger.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.DirectConversationRequestDTO;
import com.solaria.messenger.dto.request.GroupConversationRequestDTO;
import com.solaria.messenger.dto.request.ParticipantsRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.openapi.ConversationOpenApi;
import com.solaria.messenger.service.ConversationService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/messaging/conversations")
public class ConversationController implements ConversationOpenApi {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    @PostMapping("/direct")
    public ResponseEntity<ConversationResponseDTO> createDirectConversation(
            @Valid @RequestBody DirectConversationRequestDTO dto) {
        ConversationResponseDTO response = conversationService.createDirectConversation(dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.getId())).body(response);
    }

    @Override
    @PostMapping("/group")
    public ResponseEntity<ConversationResponseDTO> createGroupConversation(
            @Valid @RequestBody GroupConversationRequestDTO dto) {
        ConversationResponseDTO response = conversationService.createGroupConversation(dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.getId())).body(response);
    }

    @Override
    @PostMapping("/chatbot-conversations")
    public ResponseEntity<ConversationResponseDTO> createChatbotConversation(
            @Valid @RequestBody ChatbotConversationRequestDTO dto) {
        ConversationResponseDTO response = conversationService.createChatbotConversation(dto);
        return ResponseEntity.created(URI.create("/messaging/conversations/" + response.getId())).body(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(conversationService.findById(id));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<List<ConversationResponseDTO>> findMine() {
        return ResponseEntity.ok(conversationService.findMine());
    }

    @Override
    @PostMapping("/{id}/participants")
    public ResponseEntity<ConversationResponseDTO> addParticipants(
            @PathVariable String id,
            @Valid @RequestBody ParticipantsRequestDTO dto) {
        return ResponseEntity.ok(conversationService.addParticipants(id, dto.getUserIds()));
    }

    @Override
    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<ConversationResponseDTO> removeParticipant(
            @PathVariable String id,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(conversationService.removeParticipant(id, userId));
    }
}
