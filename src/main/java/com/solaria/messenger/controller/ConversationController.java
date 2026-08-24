package com.solaria.messenger.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.UserConversationRequestDTO;
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
    @PostMapping("/user-conversations")
    public ResponseEntity<ConversationResponseDTO> createUserConversation(
            @Valid @RequestBody UserConversationRequestDTO dto) {
        ConversationResponseDTO response = conversationService.createUserConversation(dto);
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + response.getId())).body(response);
    }

    @Override
    @PostMapping("/chatbot-conversations")
    public ResponseEntity<ConversationResponseDTO> createChatbotConversation(
            @Valid @RequestBody ChatbotConversationRequestDTO dto) {
        ConversationResponseDTO response = conversationService.createChatbotConversation(dto);
        return ResponseEntity.created(URI.create("/api/v1/conversations/" + response.getId())).body(response);
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
}
