package com.solaria.messenger.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.dto.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.openapi.ChatbotMessageOpenApi;
import com.solaria.messenger.service.MessageService;

import jakarta.validation.Valid;

=
@RestController
@RequestMapping("/internal/messages")
public class ChatbotMessageController implements ChatbotMessageOpenApi {

    private final MessageService messageService;

    public ChatbotMessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    @PostMapping
    public ResponseEntity<MessageResponseDTO> ingestChatbotMessage(@Valid @RequestBody ChatbotMessageRequestDTO dto) {
        MessageResponseDTO response = messageService.ingestChatbotMessage(dto);
        return ResponseEntity.created(URI.create("/internal/messages/" + response.getId())).body(response);
    }
}
