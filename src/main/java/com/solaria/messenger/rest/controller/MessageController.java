package com.solaria.messenger.rest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.contract.request.MessageEditRequestDTO;
import com.solaria.messenger.rest.openapi.MessageOpenApi;
import com.solaria.messenger.core.usecase.message.DeleteMessageUseCase;
import com.solaria.messenger.core.usecase.message.EditMessageUseCase;
import com.solaria.messenger.core.usecase.message.ListConversationMessagesUseCase;
import com.solaria.messenger.core.usecase.message.SendUserMessageUseCase;

import jakarta.validation.Valid;

/**
 * Controller REST dos recursos de mensagem, exposto em {@code /messaging/messages}.
 *
 * <p>
 * Atua na camada de entrada HTTP: valida o corpo ({@code @Valid}) e delega envio, edição, remoção
 * e consulta aos serviços de mensagem. O contrato da API é declarado em {@link MessageOpenApi}.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #sendMessage(MessageRequestDTO)} -> envia uma mensagem (201).</li>
 *     <li>{@link #editMessage(String, MessageEditRequestDTO)} -> edita o texto de uma mensagem própria.</li>
 *     <li>{@link #deleteMessage(String)} -> exclui logicamente uma mensagem própria.</li>
 *     <li>{@link #getMessagesByConversationId(String, Integer, Integer, Integer)} -> lista o histórico paginado.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/messages")
public class MessageController implements MessageOpenApi {

    private final SendUserMessageUseCase sendMessage;
    private final EditMessageUseCase editMessage;
    private final DeleteMessageUseCase deleteMessage;
    private final ListConversationMessagesUseCase listMessages;

    public MessageController(SendUserMessageUseCase sendMessage, EditMessageUseCase editMessage,
            DeleteMessageUseCase deleteMessage, ListConversationMessagesUseCase listMessages) {
        this.sendMessage = sendMessage;
        this.editMessage = editMessage;
        this.deleteMessage = deleteMessage;
        this.listMessages = listMessages;
    }

    /** Envia uma mensagem e devolve 201, sem cabeçalho Location. */
    @Override
    @PostMapping
    public ResponseEntity<MessageResponseDTO> sendMessage(@Valid @RequestBody MessageRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sendMessage.execute(dto));
    }

    /** Edita o texto de uma mensagem própria. */
    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<MessageResponseDTO> editMessage(@PathVariable String id, @Valid @RequestBody MessageEditRequestDTO dto) {
        return ResponseEntity.ok(editMessage.execute(id, dto));
    }

    /** Exclui logicamente uma mensagem própria e devolve o tombstone. */
    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponseDTO> deleteMessage(@PathVariable String id) {
        return ResponseEntity.ok(deleteMessage.execute(id));
    }

    /** Lista o histórico paginado por um único cursor ({@code sinceSequence} ou {@code beforeSequence}). */
    @Override
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageResponseDTO>> getMessagesByConversationId(@PathVariable String conversationId,
            @RequestParam(required = false) Integer sinceSequence,
            @RequestParam(required = false) Integer beforeSequence,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(listMessages.execute(conversationId, sinceSequence, beforeSequence, limit));
    }
}
