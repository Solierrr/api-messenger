package com.solaria.messenger.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.openapi.MessageOpenApi;
import com.solaria.messenger.service.MessageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/messaging/messages")
public class MessageController implements MessageOpenApi {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    @PostMapping
    public ResponseEntity<MessageResponseDTO> sendMessage(@Valid @RequestBody MessageRequestDTO dto) {
        MessageResponseDTO response = messageService.sendUserMessage(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageResponseDTO>> getMessagesByConversationId(@PathVariable String conversationId,
            @RequestParam(required = false) Integer sinceSequence,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(messageService.getMessagesByConversationId(conversationId, sinceSequence, limit));
    }
}
