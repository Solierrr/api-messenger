package com.solaria.messenger.controller;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;

import jakarta.validation.Valid;

import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.dto.ws.TypingRequest;
import com.solaria.messenger.dto.ws.WsErrorPayload;
import com.solaria.messenger.dto.ws.WsEventEnvelope;
import com.solaria.messenger.exception.BusinessException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.service.ConversationService;
import com.solaria.messenger.service.MessageService;
import com.solaria.messenger.service.TypingPresenceService;

/**
 * Handlers {@code @MessageMapping} de WS/STOMP
 */
@Controller
public class MessageWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(MessageWebSocketController.class);

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final TypingPresenceService typingPresenceService;
    private final RbacAuthorizationService rbac;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessageWebSocketController(MessageService messageService,
            ConversationService conversationService,
            TypingPresenceService typingPresenceService,
            RbacAuthorizationService rbac,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.typingPresenceService = typingPresenceService;
        this.rbac = rbac;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @MessageMapping("/messages.send")
    public void sendMessage(@Valid @Payload MessageRequestDTO dto) {
        MessageResponseDTO response = messageService.sendUserMessage(dto);

        WsEventEnvelope ack = WsEventEnvelope.builder()
                .type("MESSAGE_ACK")
                .conversationId(response.getConversationId())
                .eventId(response.getId())
                .serverTimestamp(Instant.now())
                .payload(response)
                .build();

        simpMessagingTemplate.convertAndSendToUser(rbac.currentUserId().toString(), "/queue/acks", ack);
    }

    /**
     * Captura exceções de negocio, evitando fechar a conexão por um erro de negocio
     */
    @MessageExceptionHandler(BusinessException.class)
    public void handleBusinessException(BusinessException ex) {
        WsErrorPayload payload = WsErrorPayload.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .traceId(null)
                .build();
        try {
            simpMessagingTemplate.convertAndSendToUser(rbac.currentUserId().toString(), "/queue/errors", payload);
        } catch (Exception sendEx) {
            log.warn("Falha ao enviar erro de negocio WS para /queue/errors", sendEx);
        }
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidationException(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        String detail = (bindingResult != null)
                ? bindingResult.getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "))
                : ex.getMessage();

        WsErrorPayload payload = WsErrorPayload.builder()
                .code("VALIDATION_ERROR")
                .message("Erro de validação nos campos da requisição" + (detail == null || detail.isBlank() ? "." : ": " + detail))
                .traceId(null)
                .build();
        try {
            simpMessagingTemplate.convertAndSendToUser(rbac.currentUserId().toString(), "/queue/errors", payload);
        } catch (Exception sendEx) {
            log.warn("Falha ao enviar erro de validação WS para /queue/errors", sendEx);
        }
    }

    @MessageMapping("/typing.started")
    public void typingStarted(@Valid @Payload TypingRequest request) {
        String userId = rbac.currentUserId().toString();
        if (typingPresenceService.startedRecently(request.getConversationId(), userId)) {
            return;
        }
        Conversation conversation = requireParticipantConversation(request.getConversationId());
        typingPresenceService.start(conversation.getId(), userId);
        publishTyping(conversation.getId(), userId, "TYPING_STARTED");
    }

    @MessageMapping("/typing.stopped")
    public void typingStopped(@Valid @Payload TypingRequest request) {
        Conversation conversation = requireParticipantConversation(request.getConversationId());
        String userId = rbac.currentUserId().toString();
        typingPresenceService.stop(conversation.getId(), userId);
        publishTyping(conversation.getId(), userId, "TYPING_STOPPED");
    }

    @MessageMapping("/presence.ping")
    public void presencePing() {
        log.debug("presence.ping recebido de userId={}", rbac.currentUserId());
    }

    private Conversation requireParticipantConversation(String conversationId) {
        Conversation conversation = conversationService.requireEntityById(conversationId);
        conversationService.requireParticipant(conversation);
        return conversation;
    }

    private void publishTyping(String conversationId, String userId, String type) {
        WsEventEnvelope envelope = WsEventEnvelope.builder()
                .type(type)
                .conversationId(conversationId)
                .serverTimestamp(Instant.now())
                .payload(Map.of("userId", userId))
                .build();
        simpMessagingTemplate.convertAndSend("/topic/conversations/" + conversationId, envelope);
    }
}
