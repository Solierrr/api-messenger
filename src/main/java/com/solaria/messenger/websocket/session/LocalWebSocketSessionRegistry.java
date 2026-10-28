package com.solaria.messenger.websocket.session;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
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
 * Guarda cada conexão física, controla a admissão (até 4 sessões por user, contadas em todos os pods) e espelha as
 * sessões admitidas no {@link RedisPresenceStore}, que dá a presença online/offline entre pods.
 * A identidade é gravada antes da consulta à Auth, para que uma remoção alcance também conexões com CONNECT em andamento.
 * Cada acesso ao mapa acontece sob o monitor ({@code synchronized}) do próprio registro; o Redis é chamado fora dele.
 * </p>
 * <ul>
 *     <li>{@link #capture(WebSocketSession)} -> registra a conexão física.</li>
 *     <li>{@link #identify(String, UUID)} / {@link #admit(String, UUID)} -> ligam a sessão ao user e a admitem.</li>
 *     <li>{@link #isOperational(String)} / {@link #isOnline(UUID)} -> estado da sessão e presença do user neste pod.</li>
 *     <li>{@link #isOnlineAnywhere(UUID)} -> presença do user em qualquer pod.</li>
 *     <li>{@link #disconnect(String)} / {@link #reject(String)} / {@link #closeUser(UUID)} -> removem ou derrubam sessões.</li>
 *     <li>{@link #presenceSnapshot(String, Set)} -> fotografia de quem está online.</li>
 *     <li>{@link #heartbeat()} -> renova este pod no Redis e avisa o OFFLINE de quem estava em pod morto.</li>
 * </ul>
 */
@Service
public class LocalWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalWebSocketSessionRegistry.class);
    private static final int MAX_SESSIONS_PER_USER = 4;

    private final Map<String, LocalSession> sessions = new HashMap<>();
    private final ApplicationEventPublisher events;
    private final RedisPresenceStore presence;
    // ordena as escritas de uma mesma sessão no Redis: sem isso um HSET atrasado ressuscitaria uma sessão já apagada
    private final Object presenceWrites = new Object();
    // época e versão usadas só quando o Redis falha
    private final String localEpoch = "local-" + UUID.randomUUID();
    private long localVersion;
    private final ExecutorService closes = Executors.newCachedThreadPool();

    public LocalWebSocketSessionRegistry(ApplicationEventPublisher events, RedisPresenceStore presence) {
        this.events = events;
        this.presence = presence;
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
        // shortcut: contar e gravar não são atômicos entre pods (dois CONNECTs simultâneos podem admitir a 5ª),
        // trocar por MULTI/Lua no Redis se o limite virar regra rígida
        if (presence.sessionCount(userId) >= MAX_SESSIONS_PER_USER) return SessionAdmission.SESSION_LIMIT;

        boolean first;
        synchronized (this) {
            // a sessão precisa estar identificada para este user, aberta e ainda não admitida
            LocalSession session = sessions.get(sessionId);
            if (session == null || session.revoked || session.admitted || !userId.equals(session.userId)
                    || !session.transport.isOpen()) {
                return SessionAdmission.REJECTED;
            }

            // contagem local e admissão no mesmo bloco, para duas conexões simultâneas deste pod não passarem do limite
            long count = sessionCount(userId);
            if (count >= MAX_SESSIONS_PER_USER) return SessionAdmission.SESSION_LIMIT;
            session.admitted = true;
            first = count == 0;
        }

        // grava no Redis só se a sessão ainda estiver ativa; um disconnect já concluído não é desfeito
        synchronized (presenceWrites) {
            if (isOperational(sessionId)) presence.add(userId, sessionId);
        }

        // primeira sessão do user neste pod: avisa o estado em todos os pods
        if (first) publishPresence(userId);
        return SessionAdmission.ADMITTED;
    }

    /** Sessão admitida, não revogada, sem DISCONNECT do cliente e com o socket aberto; revogada para de operar antes de fechar */
    public synchronized boolean isOperational(String sessionId) {
        LocalSession session = sessions.get(sessionId);
        return session != null && isActive(session) && !session.disconnecting && session.transport.isOpen();
    }

    /** O cliente mandou DISCONNECT: a sessão para de operar já, antes de o socket fechar */
    public synchronized void markDisconnecting(String sessionId) {
        LocalSession session = sessions.get(sessionId);
        if (session != null) session.disconnecting = true;
    }

    /** User ligado à sessão, ou {@code null} antes do CONNECT */
    public synchronized UUID userId(String sessionId) {
        LocalSession session = sessions.get(sessionId);
        return session == null ? null : session.userId;
    }

    /** Online neste pod = pelo menos uma sessão admitida (sem TTL, então socket aberto nunca é esquecido) */
    public synchronized boolean isOnline(UUID userId) {
        return sessionCount(userId) > 0;
    }

    /** Online em qualquer pod; sem Redis, vale a visão deste pod */
    public boolean isOnlineAnywhere(UUID userId) {
        return presence.online(userId).orElseGet(() -> isOnline(userId));
    }

    /** Remove a sessão (DISCONNECT do STOMP ou fechamento do socket); última sessão deste pod avisa a presença */
    public void disconnect(String sessionId) {
        LocalSession removed;
        boolean last;
        synchronized (this) {
            removed = sessions.remove(sessionId);
            // revogada já saiu do Redis em reject/closeUser
            if (removed == null || !isActive(removed)) return;
            last = sessionCount(removed.userId) == 0;
        }
        synchronized (presenceWrites) {
            presence.remove(removed.userId, last, sessionId);
        }
        if (last) publishPresence(removed.userId);
    }

    /** Recusa e fecha só esta sessão, inclusive quando a admissão dela falhou */
    public void reject(String sessionId) {
        LocalSession session;
        boolean wasAdmitted;
        boolean last;
        synchronized (this) {
            session = sessions.get(sessionId);
            if (session == null || session.revoked) return;

            // revoga; se era a última sessão admitida deste pod, avisa a presença
            wasAdmitted = session.admitted;
            session.revoked = true;
            last = wasAdmitted && sessionCount(session.userId) == 0;
        }
        if (wasAdmitted) {
            synchronized (presenceWrites) {
                presence.remove(session.userId, last, sessionId);
            }
        }
        if (last) publishPresence(session.userId);
        close(session);
    }

    /** Derruba todas as sessões do user neste pod, inclusive CONNECT pendente; não bloqueia reconexões futuras */
    public void closeUser(UUID userId) {
        List<LocalSession> captured;
        String[] admitted;
        boolean wasOnline;
        synchronized (this) {
            wasOnline = isOnline(userId);
            captured = sessions.values().stream().filter(session -> userId.equals(session.userId)).toList();
            admitted = captured.stream().filter(LocalWebSocketSessionRegistry::isActive)
                    .map(session -> session.transport.getId()).toArray(String[]::new);
            captured.forEach(session -> session.revoked = true);
        }

        // Redis, aviso e fechamento fora do monitor; o fechamento não espera frames em trânsito
        synchronized (presenceWrites) {
            presence.remove(userId, true, admitted);
        }
        if (wasOnline) publishPresence(userId);
        captured.forEach(this::close);
    }

    /** Quem está online entre os users informados, com época e versão lidas juntas; sem Redis, só este pod */
    public PresenceSnapshotDTO presenceSnapshot(String subscriptionId, Set<UUID> users) {
        RedisPresenceStore.Snapshot global = presence.snapshot(users);
        if (global != null) {
            return new PresenceSnapshotDTO(subscriptionId, global.epoch(), global.version(), global.online());
        }
        return localSnapshot(subscriptionId, users);
    }

    /**
     * A cada 30s renova este pod no Redis com as sessões locais e limpa pods sem heartbeat;
     * quem estava num pod morto recebe o aviso de presença
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        synchronized (presenceWrites) {
            Map<String, UUID> local;
            synchronized (this) {
                local = sessions.values().stream().filter(LocalWebSocketSessionRegistry::isActive)
                        .collect(Collectors.toMap(session -> session.transport.getId(), session -> session.userId));
            }
            presence.renew(local);
        }
        presence.reapDeadPods().forEach(this::publishPresence);
    }

    /** Encerra o executor de fechamentos ao desligar a aplicação */
    @PreDestroy
    public void shutdown() {
        closes.shutdown();
    }

    /** Fotografia só deste pod, usada quando o Redis falha */
    private synchronized PresenceSnapshotDTO localSnapshot(String subscriptionId, Set<UUID> users) {
        Map<UUID, Boolean> online = new HashMap<>();
        users.forEach(user -> online.put(user, false));
        for (LocalSession session : sessions.values()) {
            if (isActive(session) && online.containsKey(session.userId)) online.put(session.userId, true);
        }
        return new PresenceSnapshotDTO(subscriptionId, localEpoch, ++localVersion, Map.copyOf(online));
    }

    /** Sessões admitidas e não revogadas do user neste pod; chamar sempre sob o monitor */
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

    /** Publica o estado atual do user (em todos os pods) fora do monitor; falha só gera log */
    private void publishPresence(UUID userId) {
        try {
            RedisPresenceStore.Level level = presence.level(userId);
            events.publishEvent(level != null
                    ? new SessionPresenceChanged(userId, level.online(), level.epoch(), level.version())
                    : localChange(userId));
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar presença (userId={})", userId, failure);
        }
    }

    private synchronized SessionPresenceChanged localChange(UUID userId) {
        return new SessionPresenceChanged(userId, isOnline(userId), localEpoch, ++localVersion);
    }
}
