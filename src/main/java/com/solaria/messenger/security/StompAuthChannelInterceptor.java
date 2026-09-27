package com.solaria.messenger.security;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.solaria.messenger.dto.ws.WsErrorPayload;
import com.solaria.messenger.exception.BusinessException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.service.ConversationService;
import com.solaria.messenger.service.PresenceService;
import com.solaria.messenger.service.SessionRegistryService;

/**
 * Nucleo de seguranca do transporte WS/STOMP 
 *
 * <p> autenticacao acontece no primeiro frame STOMP ({@code CONNECT}) no header {@code Authorization}
 * reaproveita o mesmo {@link JwtDecoder} usado em requests https normais </p>
 * 
 * <p> após o CONNECT autenticado, o  {@link Jwt} fica no {@code sessionAttributes} do STOMP
 * assim os frames seguintes não decodificam novamente o token</p>
 */
@Component
public class StompAuthChannelInterceptor implements ExecutorChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private static final String SESSION_ATTR_JWT = "jwt";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Pattern CONVERSATION_TOPIC_PATTERN =
            Pattern.compile("^/topic/conversations/([A-Za-z0-9_-]+)$");

    /**
     * Eventos de retorno para o próprio user não precisa de autorização de conversa
    */
    private static final Set<String> ALLOWED_USER_QUEUE_DESTINATIONS =
            Set.of("/user/queue/acks", "/user/queue/errors");

    /** Unicos destinos {@code /app/**} que {@link com.solaria.messenger.controller.MessageWebSocketController} mapeia */
    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            "/app/messages.send", "/app/typing.started", "/app/typing.stopped", "/app/presence.ping");

    private final JwtDecoder jwtDecoder;
    private final SessionRegistryService sessionRegistryService;
    private final PresenceService presenceService;
    private final ConversationService conversationService;
    private final ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder,
            SessionRegistryService sessionRegistryService,
            PresenceService presenceService,
            ConversationService conversationService,
            ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider) {
        this.jwtDecoder = jwtDecoder;
        this.sessionRegistryService = sessionRegistryService;
        this.presenceService = presenceService;
        this.conversationService = conversationService;
        this.simpMessagingTemplateProvider = simpMessagingTemplateProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }
        return handleSubsequentFrame(message, accessor);
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        // limpa o contexto ao final de qualquer frame
        SecurityContextHolder.clearContext();
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Jwt jwt = extractStoredJwt(accessor);
        if (jwt != null) {
            authenticate(jwt);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        // limpa o contexto ao final do handleMessage()
        SecurityContextHolder.clearContext();
    }

    // CONNECT

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
        String token = stripBearerPrefix(authorizationHeader);

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (Exception ex) {
            log.warn("CONNECT STOMP recusado: token invalido (sessionId={}): {}",
                    accessor.getSessionId(), ex.getMessage());
            throw new StompAuthenticationException("Token invalido ou expirado.", ex);
        }

        String userId = jwt.getSubject();
        String sessionId = accessor.getSessionId();

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(SESSION_ATTR_JWT, jwt);
        }

        SessionRegistryService.Admission admission = sessionRegistryService.tryAdmit(userId, sessionId);
        if (admission == SessionRegistryService.Admission.REJECTED) {
            log.warn("CONNECT STOMP recusado: SESSION_LIMIT_EXCEEDED (userId={})", userId);
            throw new StompAuthenticationException("SESSION_LIMIT_EXCEEDED");
        }
        if (admission == SessionRegistryService.Admission.FIRST_SESSION) {
            presenceService.publishOnline(userId);
        }

        authenticate(jwt);

        StompHeaderAccessor connectAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (connectAccessor != null) {
            connectAccessor.setUser(new JwtAuthenticationToken(jwt));
        }

        return message;
    }

    private Message<?> handleSubsequentFrame(Message<?> message, StompHeaderAccessor accessor) {
        Jwt jwt = extractStoredJwt(accessor);
        if (jwt == null) {
            log.warn("Frame STOMP {} recusado: sessao nunca autenticou (sessionId={})",
                    accessor.getCommand(), accessor.getSessionId());
            throw new StompAuthenticationException("Sessao STOMP nao autenticada.");
        }

        if (jwt.getExpiresAt() != null && Instant.now().isAfter(jwt.getExpiresAt())) {
            log.warn("Frame STOMP {} recusado: token expirado durante a conexao (userId={})",
                    accessor.getCommand(), jwt.getSubject());
            throw new StompAuthenticationException("Token expirado durante a conexao.");
        }

        authenticate(jwt);

        String userId = jwt.getSubject();
        sessionRegistryService.touch(userId, accessor.getSessionId());

        StompCommand command = accessor.getCommand();
        if (command == null
                || StompCommand.UNSUBSCRIBE.equals(command)
                || StompCommand.DISCONNECT.equals(command)) {
            return message;
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            return handleSubscribe(message, accessor, userId);
        }
        if (StompCommand.SEND.equals(command)) {
            return handleSend(message, accessor, userId);
        }

        log.warn("Frame STOMP {} recusado: comando fora da allowlist (userId={}, sessionId={})",
                command, userId, accessor.getSessionId());
        return denyFrame(userId, "COMMAND_NOT_ALLOWED", "Comando STOMP nao permitido.");
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor, String userId) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return denyFrame(userId, "SUBSCRIBE_DESTINATION_REQUIRED", "SUBSCRIBE sem destino.");
        }

        if (ALLOWED_USER_QUEUE_DESTINATIONS.contains(destination)) {
            return message;
        }

        Matcher matcher = CONVERSATION_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            log.warn("SUBSCRIBE recusado (userId={}, destino={}): fora da allowlist", userId, destination);
            return denyFrame(userId, "SUBSCRIBE_NOT_ALLOWED", "Destino de subscription nao permitido.");
        }

        String conversationId = matcher.group(1);
        try {
            Conversation conversation = conversationService.requireEntityById(conversationId);
            conversationService.requireParticipant(conversation);
        } catch (BusinessException ex) {
            log.warn("SUBSCRIBE recusado (userId={}, conversationId={}): {}",
                    userId, conversationId, ex.getMessage());
            return denyFrame(userId, ex.getErrorCode(), ex.getMessage());
        }

        return message;
    }

    private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor, String userId) {
        String destination = accessor.getDestination();
        if (destination == null || !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            log.warn("SEND recusado (userId={}, destino={}): fora da allowlist", userId, destination);
            return denyFrame(userId, "SEND_NOT_ALLOWED", "Destino de SEND nao permitido.");
        }
        return message;
    }

    /** Avisa o cliente por {@code /user/queue/errors} e descarta o frame (sem fechar a conexao) */
    private Message<?> denyFrame(String userId, String code, String detail) {
        WsErrorPayload payload = WsErrorPayload.builder()
                .code(code)
                .message(detail)
                .build();
        simpMessagingTemplateProvider.getObject().convertAndSendToUser(userId, "/queue/errors", payload);
        return null;
    }

    private void authenticate(Jwt jwt) {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Jwt extractStoredJwt(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object stored = sessionAttributes.get(SESSION_ATTR_JWT);
        return (stored instanceof Jwt jwt) ? jwt : null;
    }

    private String stripBearerPrefix(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new StompAuthenticationException("Header Authorization ausente no CONNECT.");
        }
        if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return authorizationHeader;
    }
}
