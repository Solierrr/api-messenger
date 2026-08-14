package com.solaria.messenger.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.service.ConversationService;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<Conversation> createConversation(@RequestBody Conversation conversation) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.createConversation(conversation));
    }

    @GetMapping("/{id}")
    public Conversation getConversationById(@PathVariable String id) {
        return conversationService.getConversationById(id);
    }

    @GetMapping("/user/{userId}")
    public List<Conversation> getConversationsByUserId(@PathVariable Long userId) {
        return conversationService.getConversationsByUserId(userId);
    }
}
