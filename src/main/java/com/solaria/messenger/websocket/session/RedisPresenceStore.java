package com.solaria.messenger.websocket.session;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Presença online/offline compartilhada entre os pods do api-messenger, guardada no Redis só com comandos simples (sem Lua).
 *
 * <p>
 * Online = o user tem alguma sessão admitida em qualquer pod. Também guarda a época e a versão da presença, para
 * eventos de pods diferentes serem comparáveis no app. Falha do Redis vira log e valor neutro ({@code 0} ou
 * {@code null}); o chamador cai para a visão local do pod.
 * </p>
 * <p>
 * Sem estado local além do {@code podId}; seguro para chamadas concorrentes.
 * </p>
 *
 * <p><b>Depende de:</b></p>
 * <ul>
 *     <li>{@link StringRedisTemplate} -> acesso ao Redis (Upstash).</li>
 * </ul>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #sessionCount(UUID)} -> sessões do user em todos os pods (limite global).</li>
 *     <li>{@link #add(UUID, String)} / {@link #remove(UUID, boolean, String...)} -> gravam ou apagam sessões.</li>
 *     <li>{@link #online(UUID)} / {@link #level(UUID)} / {@link #snapshot(Set)} -> leem a presença.</li>
 *     <li>{@link #renew(Map)} / {@link #reapDeadPods()} -> heartbeat do pod e limpeza dos pods mortos.</li>
 * </ul>
 */
@Component
public class RedisPresenceStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceStore.class);
    // + userId -> HASH sessionId -> podId; online = hash com algum campo
    private static final String PRESENCE = "messenger:presence:";
    // + podId -> SET dos users com sessão no pod, para limpar o pod se ele morrer
    private static final String POD_USERS = "messenger:pod-users:";
    // ZSET podId -> último heartbeat (ms)
    private static final String PODS = "messenger:pods";
    private static final String EPOCH = "messenger:presence:epoch";
    private static final String VERSION = "messenger:presence:version";
    // + podId -> trava (SET NX) de quem limpa o pod morto
    private static final String REAP = "messenger:reap:";
    // shortcut: user de pod morto segue online (e contando no limite) por até ~2 min, baixar se o app precisar de menos
    private static final long POD_TIMEOUT_MS = Duration.ofSeconds(90).toMillis();
    private static final Duration REAP_LOCK = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    // UUID por processo: um container reiniciado mantém o hostname, mas não as sessões
    private final String podId = UUID.randomUUID().toString();

    public RedisPresenceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Estado de um user com a época e a versão em que foi lido.
     *
     * @param epoch época da presença; muda se o Redis perder os dados.
     * @param version versão alocada antes da leitura.
     * @param online {@code true} se há sessão em algum pod.
     */
    public record Level(String epoch, long version, boolean online) { }

    /**
     * Estado de vários users com uma época e uma versão só.
     *
     * @param epoch época da presença.
     * @param version versão alocada antes das leituras.
     * @param online presença de cada user pedido.
     */
    public record Snapshot(String epoch, long version, Map<UUID, Boolean> online) { }

    /**
     * Sessões do user em todos os pods.
     *
     * @param userId user consultado.
     * @return quantidade de sessões; {@code 0} se o Redis falhar (aí vale só o limite local).
     */
    public long sessionCount(UUID userId) {
        try {
            return count(userId);
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao contar sessões (userId={})", userId, failure);
            return 0;
        }
    }

    /**
     * Grava a sessão admitida neste pod.
     *
     * @param userId dono da sessão.
     * @param sessionId id da sessão WebSocket.
     */
    public void add(UUID userId, String sessionId) {
        try {
            redis.opsForHash().put(PRESENCE + userId, sessionId, podId);
            redis.opsForSet().add(POD_USERS + podId, userId.toString());
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao gravar sessão (userId={}, sessionId={})", userId, sessionId, failure);
        }
    }

    /**
     * Apaga as sessões do user.
     *
     * @param userId dono das sessões.
     * @param lastLocal {@code true} se era a última sessão do user neste pod (tira o user do índice do pod).
     * @param sessionIds sessões a apagar; pode ser vazio.
     */
    public void remove(UUID userId, boolean lastLocal, String... sessionIds) {
        try {
            if (sessionIds.length > 0) redis.opsForHash().delete(PRESENCE + userId, (Object[]) sessionIds);
            if (lastLocal) redis.opsForSet().remove(POD_USERS + podId, userId.toString());
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao apagar sessões (userId={})", userId, failure);
        }
    }

    /**
     * Diz se o user tem sessão em qualquer pod.
     *
     * @param userId user consultado.
     * @return a presença, ou {@code null} se o Redis falhar.
     */
    public Boolean online(UUID userId) {
        try {
            return count(userId) > 0;
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao ler presença (userId={})", userId, failure);
            return null;
        }
    }

    /**
     * Lê a presença do user com uma versão alocada depois da escrita do chamador e antes da leitura:
     * a maior versão sempre leu o estado mais novo.
     *
     * @param userId user consultado.
     * @return estado com época e versão, ou {@code null} se o Redis falhar.
     */
    public Level level(UUID userId) {
        try {
            String epoch = epoch();
            long version = redis.opsForValue().increment(VERSION);
            return new Level(epoch, version, count(userId) > 0);
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao ler presença (userId={})", userId, failure);
            return null;
        }
    }

    /**
     * Lê a presença de vários users com a versão alocada antes das leituras, como no relógio das conversas.
     *
     * @param users users consultados.
     * @return estado de todos com época e versão, ou {@code null} se o Redis falhar.
     */
    public Snapshot snapshot(Set<UUID> users) {
        try {
            String epoch = epoch();
            long version = redis.opsForValue().increment(VERSION);
            Map<UUID, Boolean> online = new HashMap<>();
            // um HLEN por user: são os participantes de uma conversa, poucos o bastante para dispensar pipeline
            for (UUID user : users) online.put(user, count(user) > 0);
            return new Snapshot(epoch, version, online);
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao montar snapshot de presença", failure);
            return null;
        }
    }

    /**
     * Renova o heartbeat do pod e regrava as sessões locais; recupera a presença se o Redis perdeu os dados
     * ou se outro pod limpou este por engano (pausa longa). Chamado a cada 30s.
     *
     * @param localSessions sessões admitidas neste pod, de sessionId para userId.
     */
    public void renew(Map<String, UUID> localSessions) {
        try {
            redis.opsForZSet().add(PODS, podId, System.currentTimeMillis());
            if (localSessions.isEmpty()) return;
            localSessions.forEach((sessionId, userId) -> {
                redis.opsForHash().put(PRESENCE + userId, sessionId, podId);
                redis.opsForSet().add(POD_USERS + podId, userId.toString());
            });
        } catch (RuntimeException failure) {
            log.warn("Redis indisponível ao renovar o heartbeat do pod", failure);
        }
    }

    /**
     * Apaga as sessões de pods sem heartbeat há 90s; cada pod morto é limpo por um só pod ({@code SET NX}).
     *
     * @return users que perderam sessões, para o chamador avisar o OFFLINE.
     */
    public Set<UUID> reapDeadPods() {
        Set<UUID> affected = new HashSet<>();
        try {
            Set<String> dead = redis.opsForZSet().rangeByScore(PODS, 0, System.currentTimeMillis() - POD_TIMEOUT_MS);
            for (String pod : dead == null ? Set.<String>of() : dead) {
                if (pod.equals(podId) || !Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(REAP + pod, podId, REAP_LOCK))) {
                    continue;
                }
                Set<String> users = redis.opsForSet().members(POD_USERS + pod);
                for (String user : users == null ? Set.<String>of() : users) {
                    // só os campos do pod morto; sessões do mesmo user em pods vivos ficam
                    Object[] stale = redis.opsForHash().entries(PRESENCE + user).entrySet().stream()
                            .filter(field -> pod.equals(field.getValue())).map(Map.Entry::getKey).toArray();
                    if (stale.length == 0) continue;
                    redis.opsForHash().delete(PRESENCE + user, stale);
                    affected.add(UUID.fromString(user));
                }
                redis.delete(POD_USERS + pod);
                redis.opsForZSet().remove(PODS, pod);
            }
        } catch (RuntimeException failure) {
            log.warn("Falha ao limpar pods sem heartbeat", failure);
        }
        return affected;
    }

    // só muda se o Redis perder os dados, e aí o app descarta as versões antigas
    private String epoch() {
        String epoch = redis.opsForValue().get(EPOCH);
        if (epoch != null) return epoch;
        redis.opsForValue().setIfAbsent(EPOCH, UUID.randomUUID().toString());
        return redis.opsForValue().get(EPOCH);
    }

    private long count(UUID userId) {
        Long size = redis.opsForHash().size(PRESENCE + userId);
        return size == null ? 0 : size;
    }
}
