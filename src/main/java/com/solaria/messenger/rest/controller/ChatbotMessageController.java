package com.solaria.messenger.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.contract.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.rest.openapi.ChatbotMessageOpenApi;
import com.solaria.messenger.core.usecase.message.IngestChatbotMessageUseCase;

import jakarta.validation.Valid;

/**
 * Controller REST interno que recebe a resposta do chatbot e a grava na conversa.
 *
 * <p>
 * Exposto em {@code /internal/messages}, sem autenticação de aplicação, pois o isolamento é feito
 * pela rede. Delega a ingestão a {@link IngestChatbotMessageUseCase}.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #ingestChatbotMessage(ChatbotMessageRequestDTO)} -> grava a resposta numa conversa com o chatbot e
 *         devolve 201.</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/messages")
public class ChatbotMessageController implements ChatbotMessageOpenApi {

    private final IngestChatbotMessageUseCase ingestChatbotMessage;

    public ChatbotMessageController(IngestChatbotMessageUseCase ingestChatbotMessage) {
        this.ingestChatbotMessage = ingestChatbotMessage;
    }

    /** Ingere a resposta do pipeline LLM numa conversa CHAT_BOT e devolve 201. */
    @Override
    @PostMapping
    public ResponseEntity<MessageResponseDTO> ingestChatbotMessage(@Valid @RequestBody ChatbotMessageRequestDTO dto) {
        MessageResponseDTO response = ingestChatbotMessage.execute(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
