package com.solaria.messenger.websocket.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.websocket.event.ConversationSubscriptionRegistered;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;
import com.solaria.messenger.websocket.enums.SessionAdmission;
import com.solaria.messenger.websocket.exception.StompAuthenticationException;

/**
 * Interceptor de segurança do WebSocket/STOMP
 *
 * <p>
 * A autenticação acontece no primeiro frame ({@code CONNECT}), pelo header {@code Authorization}, com o mesmo
 * {@link JwtDecoder} do HTTP. O {@link Jwt} fica guardado na sessão STOMP e os frames seguintes não decodificam
 * o token de novo. A expiração é conferida em cada frame; user ativo na Auth só no CONNECT;
 * participação na conversa em SEND/SUBSCRIBE.
 * </p>
 * <ul>
 *     <li>{@link #preSend(Message, MessageChannel)} -> autentica o CONNECT ou valida os frames seguintes.</li>
 *     <li>{@link #beforeHandle(Message, MessageChannel, MessageHandler)} -> revalida token, sessão e destino na thread do handler.</li>
 *     <li>{@link #afterMessageHandled(Message, MessageChannel, MessageHandler, Exception)} -> avisa a inscrição já registrada no broker.</li>
 *     <li>{@link #afterSendCompletion(Message, MessageChannel, boolean, Exception)} -> limpa o contexto de segurança.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ExecutorChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private static final String SESSION_ATTR_JWT = "jwt";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final StompDestinationAuthorizer destinations;
    private final ApplicationEventPublisher events;
    private final UserAccessService userAccessService;
    private final LocalWebSocketSessionRegistry sessions;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder, StompDestinationAuthorizer destinations,
            ApplicationEventPublisher events,
            UserAccessService userAccessService, LocalWebSocketSessionRegistry sessions) {
        this.jwtDecoder = jwtDecoder;
        this.destinations = destinations;
        this.events = events;
        this.userAccessService = userAccessService;
        this.sessions = sessions;
    }

    /** CONNECT autentica a sessão; os outros frames usam o JWT já guardado */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) return handleConnect(message, accessor);
        return handleSubsequentFrame(message, accessor);
    }

    /** Limpa o contexto de segurança ao fim de qualquer frame */
    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        SecurityContextHolder.clearContext();
    }

    /** Na thread do handler, confere de novo expiração, sessão e acesso ao destino */
    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        Jwt jwt = extractStoredJwt(accessor);
        if (jwt == null) return message;

        // token pode ter vencido entre o preSend e a execução
        if (isExpired(jwt)) throw new StompAuthenticationException("Token expirado durante a conexão.");

        // sessão revogada no meio do caminho: descarta o frame em silêncio
        StompCommand command = accessor.getCommand();
        if ((command == StompCommand.SEND || command == StompCommand.SUBSCRIBE)
                && !sessions.isOperational(accessor.getSessionId())) return null;

        // autentica a thread e confere a participação na conversa do destino
        authenticate(jwt);
        if (command == StompCommand.SUBSCRIBE) return destinations.authorizeSubscribe(message, accessor, canonicalUserId(jwt));
        if (command == StompCommand.SEND) return destinations.authorizeResource(message, canonicalUserId(jwt));
        return message;
    }

    /** Publica o evento de inscrição só depois que o broker registrou o SUBSCRIBE */
    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        try {
            // só interessa SUBSCRIBE processado sem erro pelo broker
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            if (ex != null || !(handler instanceof AbstractBrokerMessageHandler)
                    || accessor.getCommand() != StompCommand.SUBSCRIBE) return;

            // inscrição num tópico de conversa, com subscriptionId e sessão ainda ativa
            String conversationId = StompDestinations.conversationTopicId(accessor.getDestination());
            Jwt jwt = extractStoredJwt(accessor);
            if (jwt != null && conversationId != null && accessor.getSubscriptionId() != null
                    && !accessor.getSubscriptionId().isBlank() && sessions.isOperational(accessor.getSessionId())) {
                events.publishEvent(new ConversationSubscriptionRegistered(accessor.getSessionId(),
                        accessor.getSubscriptionId(), UUID.fromString(canonicalUserId(jwt)), conversationId));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Autentica e admite a sessão; se ela for encerrada no meio, o CONNECT também falha */
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        try {
            // decodifica o token e exige subject em formato UUID
            Jwt jwt = jwtDecoder.decode(stripBearerPrefix(accessor.getFirstNativeHeader("Authorization")));
            String subject = jwt.getSubject();
            if (subject == null || !subject.matches(
                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw new StompAuthenticationException("Subject do JWT inválido.");
            }

            // liga a sessão ao user (só uma vez) e exige user ativo na Auth
            UUID authId = UUID.fromString(subject);
            if (!sessions.identify(sessionId, authId)) {
                throw new StompAuthenticationException("Sessão encerrada ou CONNECT repetido.");
            }
            userAccessService.requireActiveActor(authId);

            // guarda o JWT na sessão e define o user do frame
            Map<String, Object> attributes = accessor.getSessionAttributes();
            StompHeaderAccessor original = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (attributes == null || original == null) {
                throw new StompAuthenticationException("Sessão STOMP indisponível.");
            }
            attributes.put(SESSION_ATTR_JWT, jwt);
            original.setUser(new JwtAuthenticationToken(jwt, List.of(), authId.toString()));

            // admite a sessão no registro (respeitando o limite por user)
            SessionAdmission admission = sessions.admit(sessionId, authId);
            if (admission == SessionAdmission.SESSION_LIMIT) {
                throw new StompAuthenticationException("SESSION_LIMIT_EXCEEDED");
            }
            if (admission != SessionAdmission.ADMITTED) {
                throw new StompAuthenticationException("A sessão foi encerrada durante o CONNECT.");
            }

            authenticate(jwt);
            return message;

        } catch (RuntimeException failure) {
            sessions.reject(sessionId);
            if (failure instanceof BusinessException || failure instanceof StompAuthenticationException) throw failure;
            // não registrar token nem detalhes do decoder em log de autenticação
            throw new StompAuthenticationException("Token inválido ou sessão indisponível.", failure);
        }
    }

    /** Frames depois do CONNECT: exige sessão autenticada, token válido e comando permitido */
    private Message<?> handleSubsequentFrame(Message<?> message, StompHeaderAccessor accessor) {
        // sessão precisa ter feito CONNECT e o token não pode ter vencido
        Jwt jwt = extractStoredJwt(accessor);
        if (jwt == null) {
            log.warn("Frame STOMP {} recusado: sessao nunca autenticou (sessionId={})",
                    accessor.getCommand(), accessor.getSessionId());
            throw new StompAuthenticationException("Sessao STOMP nao autenticada.");
        }
        if (isExpired(jwt)) {
            log.warn("Frame STOMP {} recusado: token expirado durante a conexao (userId={})",
                    accessor.getCommand(), jwt.getSubject());
            throw new StompAuthenticationException("Token expirado durante a conexao.");
        }

        authenticate(jwt);
        String userId = canonicalUserId(jwt);
        StompCommand command = accessor.getCommand();

        // o DISCONNECT é marcado antes de o relay encaminhá-lo; o ERROR de broker fechado que vem depois é esperado
        if (StompCommand.DISCONNECT.equals(command)) sessions.markDisconnecting(accessor.getSessionId());

        // heartbeat, UNSUBSCRIBE e DISCONNECT passam direto
        if (command == null || StompCommand.UNSUBSCRIBE.equals(command) || StompCommand.DISCONNECT.equals(command)) {
            return message;
        }

        // SUBSCRIBE e SEND: sessão ativa e destino autorizado
        if (StompCommand.SUBSCRIBE.equals(command)) {
            if (!sessions.isOperational(accessor.getSessionId())) return null;
            return destinations.authorizeSubscribe(message, accessor, userId);
        }
        if (StompCommand.SEND.equals(command)) {
            if (!sessions.isOperational(accessor.getSessionId())) return null;
            return destinations.authorizeSend(message, accessor, userId);
        }

        // qualquer outro comando é recusado
        log.warn("Frame STOMP {} recusado: comando fora da allowlist (userId={}, sessionId={})",
                command, userId, accessor.getSessionId());
        return destinations.deny(userId, "COMMAND_NOT_ALLOWED", "Comando STOMP nao permitido.");
    }

    private static boolean isExpired(Jwt jwt) {
        return jwt.getExpiresAt() != null && !Instant.now().isBefore(jwt.getExpiresAt());
    }

    /** Coloca o user do JWT no contexto de segurança da thread atual */
    private void authenticate(Jwt jwt) {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of(), canonicalUserId(jwt));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** Subject do JWT normalizado como UUID canônico */
    private static String canonicalUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject()).toString();
    }

    /** JWT guardado na sessão STOMP, ou null se a sessão não autenticou */
    private Jwt extractStoredJwt(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) return null;
        return sessionAttributes.get(SESSION_ATTR_JWT) instanceof Jwt jwt ? jwt : null;
    }

    /** Aceita o token com ou sem {@code Bearer}; sem header, o CONNECT é recusado */
    private String stripBearerPrefix(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new StompAuthenticationException("Header Authorization ausente no CONNECT.");
        }
        return authorizationHeader.startsWith(BEARER_PREFIX)
                ? authorizationHeader.substring(BEARER_PREFIX.length())
                : authorizationHeader;
    }
}
