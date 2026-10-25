package com.solaria.messenger.websocket.session;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.solaria.messenger.websocket.dto.response.PresenceSnapshotDTO;

import jakarta.annotation.PreDestroy;
import com.solaria.messenger.websocket.enums.SessionAdmission;
import com.solaria.messenger.websocket.event.SessionPresenceChanged;

/**
 * Registro local das sessões WebSocket desta instância
 *
 * <p>
 * Guarda cada conexão física, controla a admissão (até 4 sessões por user) e calcula a presença online/offline.
 * A identidade é gravada antes da consulta à Auth, para que uma remoção alcance também conexões com CONNECT em andamento.
 * Cada acesso ao mapa acontece sob o monitor ({@code synchronized}) do próprio registro.
 * </p>
 * <ul>
 *     <li>{@link #capture(WebSocketSession)} -> registra a conexão física.</li>
 *     <li>{@link #identify(String, UUID)} / {@link #admit(String, UUID)} -> ligam a sessão ao user e a admitem.</li>
 *     <li>{@link #isOperational(String)} / {@link #isOnline(UUID)} -> estado da sessão e presença do user.</li>
 *     <li>{@link #disconnect(String)} / {@link #reject(String)} / {@link #closeUser(UUID)} -> removem ou derrubam sessões.</li>
 *     <li>{@link #presenceSnapshot(String, Set)} -> fotografia de quem está online.</li>
 * </ul>
 */
@Service
public class LocalWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalWebSocketSessionRegistry.class);
    private static final int MAX_SESSIONS_PER_USER = 4;

    private final Map<String, LocalSession> sessions = new HashMap<>();
    private final ApplicationEventPublisher events;
    private final String presenceEpoch = UUID.randomUUID().toString();
    private long presenceVersion;
    private final ExecutorService closes = Executors.newCachedThreadPool();

    public LocalWebSocketSessionRegistry(ApplicationEventPublisher events) {
        this.events = events;
    }

    /** Registra a conexão física recém-aberta */
    public synchronized void capture(WebSocketSession transport) {
        if (sessions.putIfAbsent(transport.getId(), new LocalSession(transport)) != null) {
            throw new IllegalStateException("Sessão WebSocket já registrada.");
        }
    }

    /** Liga a sessão ao user do token; um segundo CONNECT na mesma sessão é recusado */
    public synchronized boolean identify(String sessionId, UUID userId) {
        LocalSession session = sessions.get(sessionId);
        if (session == null || session.revoked || session.userId != null || !session.transport.isOpen()) {
            return false;
        }
        session.userId = userId;
        return true;
    }

    /** Admite a sessão se o user ainda tiver vaga; a 5ª conexão é recusada e não derruba as outras */
    public SessionAdmission admit(String sessionId, UUID userId) {
        SessionPresenceChanged change;
        synchronized (this) {
            // a sessão precisa estar identificada para este user, aberta e ainda não admitida
            LocalSession session = sessions.get(sessionId);
            if (session == null || session.revoked || session.admitted || !userId.equals(session.userId)
                    || !session.transport.isOpen()) {
                return SessionAdmission.REJECTED;
            }

            // contagem e admissão no mesmo bloco, para duas conexões simultâneas não passarem do limite
            long count = sessionCount(userId);
            if (count >= MAX_SESSIONS_PER_USER) return SessionAdmission.SESSION_LIMIT;
            session.admitted = true;

            // primeira sessão do user: ele fica online
            change = count == 0 ? new SessionPresenceChanged(userId, true, ++presenceVersion) : null;
        }
        publishPresence(change);
        return SessionAdmission.ADMITTED;
    }

    /** Sessão admitida, não revogada e com o socket aberto; revogada para de operar antes de fechar */
    public synchronized boolean isOperational(String sessionId) {
        LocalSession session = sessions.get(sessionId);
        return session != null && isActive(session) && session.transport.isOpen();
    }

    /** User ligado à sessão, ou {@code null} antes do CONNECT */
    public synchronized UUID userId(String sessionId) {
        LocalSession session = sessions.get(sessionId);
        return session == null ? null : session.userId;
    }

    /** Online = pelo menos uma sessão admitida (sem TTL, então socket aberto nunca é esquecido) */
    public synchronized boolean isOnline(UUID userId) {
        return sessionCount(userId) > 0;
    }

    /** Remove a sessão (DISCONNECT do STOMP ou fechamento do socket); última sessão deixa o user offline */
    public void disconnect(String sessionId) {
        SessionPresenceChanged change = null;
        synchronized (this) {
            LocalSession removed = sessions.remove(sessionId);
            if (removed != null && isActive(removed) && sessionCount(removed.userId) == 0) {
                change = new SessionPresenceChanged(removed.userId, false, ++presenceVersion);
            }
        }
        publishPresence(change);
    }

    /** Recusa e fecha só esta sessão, inclusive quando a admissão dela falhou */
    public void reject(String sessionId) {
        LocalSession session;
        SessionPresenceChanged change = null;
        synchronized (this) {
            session = sessions.get(sessionId);
            if (session == null || session.revoked) return;

            // revoga; se era a última sessão admitida, o user fica offline
            boolean wasAdmitted = session.admitted;
            session.revoked = true;
            if (wasAdmitted && sessionCount(session.userId) == 0) {
                change = new SessionPresenceChanged(session.userId, false, ++presenceVersion);
            }
        }
        publishPresence(change);
        close(session);
    }

    /** Derruba todas as sessões do user, inclusive CONNECT pendente; não bloqueia reconexões futuras */
    public void closeUser(UUID userId) {
        List<LocalSession> captured;
        SessionPresenceChanged change;
        synchronized (this) {
            boolean wasOnline = isOnline(userId);
            captured = sessions.values().stream().filter(session -> userId.equals(session.userId)).toList();
            captured.forEach(session -> session.revoked = true);
            change = wasOnline ? new SessionPresenceChanged(userId, false, ++presenceVersion) : null;
        }

        // aviso e fechamento fora do monitor; o fechamento não espera frames em trânsito
        publishPresence(change);
        captured.forEach(this::close);
    }

    /** Quem está online entre os users informados, com época e versão lidas juntas */
    public synchronized PresenceSnapshotDTO presenceSnapshot(String subscriptionId, Set<UUID> users) {
        Map<UUID, Boolean> online = new HashMap<>();
        users.forEach(user -> online.put(user, false));
        for (LocalSession session : sessions.values()) {
            if (isActive(session) && online.containsKey(session.userId)) online.put(session.userId, true);
        }
        return new PresenceSnapshotDTO(subscriptionId, presenceEpoch, presenceVersion, Map.copyOf(online));
    }

    /** Época da presença desta instância (muda a cada restart) */
    public String presenceEpoch() { return presenceEpoch; }

    /** Encerra o executor de fechamentos ao desligar a aplicação */
    @PreDestroy
    public void shutdown() {
        closes.shutdown();
    }

    /** Sessões admitidas e não revogadas do user; chamar sempre sob o monitor */
    private long sessionCount(UUID userId) {
        return sessions.values().stream()
                .filter(session -> userId.equals(session.userId) && isActive(session))
                .count();
    }

    private static boolean isActive(LocalSession session) {
        return session.admitted && !session.revoked;
    }

    /** Fecha o socket numa thread separada e limpa o registro se ele de fato fechou */
    private void close(LocalSession session) {
        closes.execute(() -> {
            try {
                session.transport.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException | RuntimeException failure) {
                // o acesso local já está negado; falha no close não desfaz a remoção no Mongo
                log.warn("Falha ao fechar sessão WebSocket local (sessionId={})", session.transport.getId(), failure);
            } finally {
                if (!session.transport.isOpen()) disconnect(session.transport.getId());
            }
        });
    }

    /** Publica a mudança de presença fora do monitor; falha só gera log */
    private void publishPresence(SessionPresenceChanged change) {
        if (change == null) return;
        try {
            events.publishEvent(change);
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar presença local (userId={}, online={})", change.userId(), change.online(), failure);
        }
    }
}
