package com.solaria.messenger.core.helper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import com.solaria.messenger.core.integration.notify.NotifyClient;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.port.UserPresence;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;

import jakarta.annotation.PreDestroy;

/**
 * Avisa por push quem está offline quando chega uma mensagem de conversa DIRECT ou GROUP
 *
 * <p>
 * Roda fora da requisição, numa fila limitada: o envio da mensagem nunca espera nem falha por causa do api-notify.
 * Fica desligado sem URL do api-notify. Pula o remetente, quem está online, quem silenciou ou arquivou a conversa
 * e conversas CHAT_BOT. Os logs só levam ids de conversa e de mensagem.
 * </p>
 * <ul>
 *     <li>{@link #notifyOffline(Message)} -> agenda o aviso aos participantes offline de uma mensagem já gravada.</li>
 * </ul>
 */
@Component
public class OfflineMessageNotifier {

    private static final Logger log = LoggerFactory.getLogger(OfflineMessageNotifier.class);
    private static final int PREVIEW_LIMIT = 100;
    private static final int TITLE_LIMIT = 120;
    private static final int MAX_RECIPIENTS = 50;
    private static final String TYPE = "NEW_MESSAGE";
    private static final String DEFAULT_TITLE = "Nova mensagem";
    private static final String DEFAULT_BODY = "Você recebeu uma nova mensagem.";

    private final NotifyClient notify;
    private final ConversationRepository conversations;
    private final ConversationAccessHelper access;
    private final ConversationUserStateRepository states;
    private final UserPresence presence;
    private final ThreadPoolExecutor executor;

    public OfflineMessageNotifier(NotifyClient notify, ConversationRepository conversations,
            ConversationAccessHelper access, ConversationUserStateRepository states,
            UserPresence presence) {
        this.notify = notify;
        this.conversations = conversations;
        this.access = access;
        this.states = states;
        this.presence = presence;
        AtomicInteger threads = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                task -> {
                    Thread thread = new Thread(task, "offline-notify-" + threads.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        this.executor.allowCoreThreadTimeOut(true);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Agenda o aviso e volta na hora; fila cheia ou qualquer falha só gera log. */
    public void notifyOffline(Message message) {
        if (!notify.isEnabled() || !isUserMessage(message)) return;
        try {
            executor.execute(() -> send(message));
        } catch (RejectedExecutionException full) {
            log.warn("Fila de avisos offline cheia; mensagem sem push (conversationId={}, messageId={})",
                    message.getConversationId(), message.getId());
        }
    }

    /** Resolve os destinatários e chama o api-notify um a um; nunca lança. */
    void send(Message message) {
        try {
            Conversation conversation = conversations.findById(message.getConversationId()).orElse(null);
            if (conversation == null || conversation.getConversationType() == ConversationType.CHAT_BOT) return;

            List<UUID> recipients = offlineRecipients(conversation, message);
            if (recipients.isEmpty()) return;

            String title = title(conversation);
            String body = preview(message.getContent());
            Map<String, String> data = Map.of("conversationId", conversation.getId(), "messageId", message.getId());
            for (UUID recipient : recipients) {
                try {
                    notify.dispatch(recipient, TYPE, title, body, data);
                } catch (ResourceAccessException unreachable) {
                    log.warn("api-notify inacessível; avisos restantes descartados (conversationId={}, messageId={})",
                            conversation.getId(), message.getId());
                    return;
                } catch (RuntimeException failure) {
                    log.warn("Falha ao enviar push de mensagem ({}) (conversationId={}, messageId={})",
                            failure.getClass().getSimpleName(), conversation.getId(), message.getId());
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Falha ao preparar push de mensagem ({}) (conversationId={}, messageId={})",
                    failure.getClass().getSimpleName(), message.getConversationId(), message.getId());
        }
    }

    private static boolean isUserMessage(Message message) {
        return message != null && message.getId() != null && message.getConversationId() != null
                && message.getSenderId() != null
                && (message.getMessageType() == MessageType.USER_TO_USER
                        || message.getMessageType() == MessageType.USER_TO_GROUP);
    }

    /** Participantes autorizados, exceto remetente, online e quem silenciou ou arquivou. */
    private List<UUID> offlineRecipients(Conversation conversation, Message message) {
        Set<UUID> candidates = new LinkedHashSet<>(access.authorizedParticipants(List.of(conversation))
                .getOrDefault(conversation.getId(), Set.of()));
        candidates.remove(message.getSenderId());
        candidates.removeIf(this::isOnline);
        if (candidates.isEmpty()) return List.of();

        Set<UUID> silenced = new LinkedHashSet<>();
        for (ConversationUserState state : states.findByUsersAndConversations(candidates, Set.of(conversation.getId()))) {
            if (Boolean.TRUE.equals(state.getMuted()) || Boolean.TRUE.equals(state.getArchived())) {
                silenced.add(state.getUserId());
            }
        }
        return candidates.stream().filter(user -> !silenced.contains(user)).limit(MAX_RECIPIENTS).toList();
    }

    /** Qualquer falha da presença trata o user como offline. */
    private boolean isOnline(UUID userId) {
        try {
            return presence.isOnline(userId);
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static String title(Conversation conversation) {
        String groupTitle = conversation.getConversationType() == ConversationType.GROUP ? conversation.getTitle() : null;
        if (groupTitle == null || groupTitle.isBlank()) return DEFAULT_TITLE;
        return truncate(DEFAULT_TITLE + " em " + groupTitle.strip(), TITLE_LIMIT);
    }

    /** Prévia de uma linha com no máximo 100 caracteres; o texto inteiro nunca sai daqui. */
    private static String preview(String content) {
        String text = content == null ? "" : content.replaceAll("\\s+", " ").strip();
        return text.isEmpty() ? DEFAULT_BODY : truncate(text, PREVIEW_LIMIT);
    }

    private static String truncate(String text, int limit) {
        if (text.length() <= limit) return text;
        int end = limit - 3;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "...";
    }
}
