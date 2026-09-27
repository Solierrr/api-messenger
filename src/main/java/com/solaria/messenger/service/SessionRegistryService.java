package com.solaria.messenger.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Registro de sessoes STOMP ativas por usuario
 */
@Service
public class SessionRegistryService {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistryService.class);

    private static final Duration SESSION_TTL = Duration.ofHours(1);

    /** Resultado de {@link #tryAdmit} */
    public enum Admission {
        /** Limite de sessoes por usuario | conexão recusada */
        REJECTED,
        /** Sessao admitida porém o usuario já tinha sessões antes*/
        ADMITTED,
        /** Sessao admitida e é a única do usuario | dispara presenca ONLINE */
        FIRST_SESSION
    }

    private final Map<String, Map<String, Instant>> sessionsByUser = new ConcurrentHashMap<>();

    @Value("${app.websocket.max-sessions-per-user}")
    private int maxSessionsPerUser;


    public Admission tryAdmit(String userId, String sessionId) {
        Admission[] outcome = new Admission[1];

        sessionsByUser.compute(userId, (key, sessions) -> {
            Map<String, Instant> current = (sessions != null) ? sessions : new ConcurrentHashMap<>();

            if (current.containsKey(sessionId)) {
                current.put(sessionId, Instant.now());
                outcome[0] = Admission.ADMITTED;
                return current;
            }
            if (current.size() >= maxSessionsPerUser) {
                outcome[0] = Admission.REJECTED;
                return current.isEmpty() ? null : current;
            }

            boolean wasEmpty = current.isEmpty();
            current.put(sessionId, Instant.now());
            outcome[0] = wasEmpty ? Admission.FIRST_SESSION : Admission.ADMITTED;
            return current;
        });

        if (outcome[0] == Admission.REJECTED) {
            log.warn("Sessao recusada: limite de {} sessoes simultaneas excedido (userId={})",
                    maxSessionsPerUser, userId);
        }
        return outcome[0];
    }

    public boolean release(String userId, String sessionId) {
        boolean[] wasLastSession = new boolean[1];

        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            if (sessions.remove(sessionId) != null && sessions.isEmpty()) {
                wasLastSession[0] = true;
                return null;
            }
            return sessions.isEmpty() ? null : sessions;
        });

        return wasLastSession[0];
    }

    /** Renova o TTL individual da sessao */
    public void touch(String userId, String sessionId) {
        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.computeIfPresent(sessionId, (sid, lastSeen) -> Instant.now());
            return sessions;
        });
    }

    /** Existe alguma sessao ativa deste usuario. */
    public boolean isOnline(String userId) {
        return sessionCount(userId) > 0;
    }

    /** Numero de sessoes atualmente registradas para o usuario. */
    public long sessionCount(String userId) {
        Map<String, Instant> sessions = sessionsByUser.get(userId);
        return (sessions != null) ? sessions.size() : 0L;
    }

    @Scheduled(fixedDelay = 300_000)
    public void reconcileExpiredSessions() {
        reconcileExpiredSessions(Instant.now());
    }

    void reconcileExpiredSessions(Instant now) {
        Instant threshold = now.minus(SESSION_TTL);
        for (String userId : sessionsByUser.keySet()) {
            sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
                sessions.values().removeIf(lastSeen -> lastSeen.isBefore(threshold));
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }
}
