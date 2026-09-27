package com.solaria.messenger.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Rastreamento  de "quem esta digitando em qual conversa"
 * usado para permitir que o {@code WebSocketSessionEventListener} emita {@code TYPING_STOPPED}
 * automaticamente quando uma sessao cai no meio de uma digitacao
 */
@Service
public class TypingPresenceService {

    private static final long TYPING_TTL_SECONDS = 5;

    private static final long TYPING_DEDUP_SECONDS = 2;

    private final Map<String, Set<String>> typingByConversation = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> conversationsByUser = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastStartedAt = new ConcurrentHashMap<>();

    /** Marca o usuario como digitando na conversa | renova o TTL */
    public void start(String conversationId, String userId) {
        typingByConversation.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        conversationsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conversationId);
        lastStartedAt.put(entryKey(conversationId, userId), Instant.now());
    }

    /** Remove o usuario da digitacao daquela conversa*/
    public void stop(String conversationId, String userId) {
        removeEntry(conversationId, userId);
    }

    /**
     * {@code true} se este usuario ja sinalizou "digitando" nesta conversa
     *  ha menos de {@value #TYPING_DEDUP_SECONDS} segundos
     */
    public boolean startedRecently(String conversationId, String userId) {
        Instant last = lastStartedAt.get(entryKey(conversationId, userId));
        return last != null && last.isAfter(Instant.now().minusSeconds(TYPING_DEDUP_SECONDS));
    }

    /**
     * encerra e retorna todas as conversas em que o usuario estava digitando 
     * no momento em que a sessao caiu para que o chamador emita
     * {@code TYPING_STOPPED} nelas
     */
    public Set<String> stopAllForUser(String userId) {
        Set<String> conversationIds = conversationsByUser.remove(userId);
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Set.of();
        }

        for (String conversationId : conversationIds) {
            Set<String> typingUsers = typingByConversation.get(conversationId);
            if (typingUsers != null) {
                typingUsers.remove(userId);
                if (typingUsers.isEmpty()) {
                    typingByConversation.remove(conversationId);
                }
            }
            lastStartedAt.remove(entryKey(conversationId, userId));
        }
        return conversationIds;
    }

    /**
     * Varre as entradas a cada 5s e remove 
     * qualquer entrada mais velha que o TTL de 5s
     */
    @Scheduled(fixedDelay = 5000)
    public void expireStaleEntries() {
        Instant threshold = Instant.now().minusSeconds(TYPING_TTL_SECONDS);
        for (Map.Entry<String, Instant> entry : lastStartedAt.entrySet()) {
            if (entry.getValue().isBefore(threshold)) {
                String[] parts = splitEntryKey(entry.getKey());
                removeEntry(parts[0], parts[1]);
            }
        }
    }

    private void removeEntry(String conversationId, String userId) {
        Set<String> typingUsers = typingByConversation.get(conversationId);
        if (typingUsers != null) {
            typingUsers.remove(userId);
            if (typingUsers.isEmpty()) {
                typingByConversation.remove(conversationId);
            }
        }

        Set<String> userConversations = conversationsByUser.get(userId);
        if (userConversations != null) {
            userConversations.remove(conversationId);
            if (userConversations.isEmpty()) {
                conversationsByUser.remove(userId);
            }
        }

        lastStartedAt.remove(entryKey(conversationId, userId));
    }

    private String entryKey(String conversationId, String userId) {
        return conversationId + "|" + userId;
    }

    private String[] splitEntryKey(String key) {
        int separatorIndex = key.indexOf('|');
        return new String[] { key.substring(0, separatorIndex), key.substring(separatorIndex + 1) };
    }
}
