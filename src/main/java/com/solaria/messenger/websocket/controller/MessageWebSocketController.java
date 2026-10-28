package com.solaria.messenger.websocket.controller;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.message.MessageSendService;
import com.solaria.messenger.websocket.dto.request.TypingRequest;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.presence.TypingService;
import com.solaria.messenger.websocket.security.StompDestinations;

import jakarta.validation.Valid;

/**
 * entrada dos comandos STOMP enviados pelo cliente por websocket
 * rota exposta por prefixo {@code /app}
 *
 * <p>
 *  funcionamento:
 * 1 - recebe o frame
 * 2 - valida o payload ({@code @Valid})
 * 3 - delega regras de negócio para services em {@code core}
 * </p>
 *
 * <p>
 * Os destinos aceitos são declarados em {@link com.solaria.messenger.websocket.security.StompDestinationAuthorizer}.
 * Os erros são tratados em {@link com.solaria.messenger.websocket.error.StompErrorHandler} 
 * e devolvidos em {@code /topic/users.{userId}.errors}, sem encerrar a conexão
 * </p>
 *
 * <p><b>Depende de:</b></p>
 * <ul>
 *     <li>{@link MessageSendService} -> persiste a mensagem e dispara as notificações, igual ao REST.</li>
 *     <li>{@link TypingService} -> valida a participação e publica a indicação de digitação.</li>
 *     <li>{@link RbacAuthorizationService} -> fornece o id do usuário autenticado.</li>
 *     <li>{@link SimpMessagingTemplate} -> envia o ACK ao tópico privado do remetente.</li>
 * </ul>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #sendMessage(MessageRequestDTO)} -> {@code /app/messages.send}: envia a mensagem e confirma com {@code MESSAGE_ACK} em {@code /topic/users.{userId}.acks}.</li>
 *     <li>{@link #typingStarted(TypingRequest)} -> {@code /app/typing.started}: indica que o usuário começou a digitar.</li>
 *     <li>{@link #typingStopped(TypingRequest)} -> {@code /app/typing.stopped}: indica que parou de digitar.</li>
 *     <li>{@link #presencePing()} -> {@code /app/presence.ping}: mantém a conexão ativa, sem alterar a presença.</li>
 * </ul>
 */
@Controller
public class MessageWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(MessageWebSocketController.class);

    private final MessageSendService messageService;
    private final TypingService typing;
    private final RbacAuthorizationService rbac;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessageWebSocketController(MessageSendService messageService,
            TypingService typing,
            RbacAuthorizationService rbac,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.messageService = messageService;
        this.typing = typing;
        this.rbac = rbac;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    /** Persiste a mensagem e confirma ao remetente em {@code /topic/users.{userId}.acks} */
    @MessageMapping("/messages.send")
    public void sendMessage(@Valid @Payload MessageRequestDTO dto) {

        // valida regra de negócios e insere no MongoDB
        MessageResponseDTO response = messageService.sendUserMessage(dto);

        // tenta enviar o ACK no tópico de acks do remetente
        WsEventEnvelope ack = WsEventEnvelope.builder()
                .type("MESSAGE_ACK")
                .conversationId(response.getConversationId())
                .eventId(response.getId())
                .serverTimestamp(Instant.now())
                .payload(response)
                .build();
        try {
            simpMessagingTemplate.convertAndSend(StompDestinations.userTopic(rbac.currentUserId(), "acks"), ack);
        } catch (RuntimeException publicationFailure) {
            log.warn("Falha ao publicar ACK de mensagem persistida (messageId={})", response.getId(), publicationFailure);
        }
    }

    /** Typing é transitório | participação e status continuam validados no domínio. */
    @MessageMapping("/typing.started")
    public void typingStarted(@Valid @Payload TypingRequest request) {
        typing.start(request.getConversationId(), rbac.currentUserId());
    }

    /** Encerra a indicação de digitação do usuário na conversa */
    @MessageMapping("/typing.stopped")
    public void typingStopped(@Valid @Payload TypingRequest request) {
        typing.stop(request.getConversationId(), rbac.currentUserId());
    }

    /** Mantém a conexão ativa; a presença depende das sessões admitidas, não deste ping */
    @MessageMapping("/presence.ping")
    public void presencePing() {
        log.debug("presence.ping recebido de userId={}", rbac.currentUserId());
    }
}
