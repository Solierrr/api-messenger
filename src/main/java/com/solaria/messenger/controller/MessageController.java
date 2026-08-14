package com.solaria.messenger.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.model.Message;
import com.solaria.messenger.service.MessageService;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<Message> sendMessage(@RequestBody Message message) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMessage(message));
    }

    @GetMapping("/conversation/{conversationId}")
    public Page<Message> getMessagesByConversationId(
            @PathVariable String conversationId,
            @PageableDefault(size = 20) Pageable pageable) {
        return messageService.getMessagesByConversationId(conversationId, pageable);
    }
}
