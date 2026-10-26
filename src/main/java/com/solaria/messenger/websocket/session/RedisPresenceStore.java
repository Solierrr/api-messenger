package com.solaria.messenger.websocket.session;

import java.util.HashMap;
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
 * </ul>
 */
@Component
public class RedisPresenceStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceStore.class);
    // + userId -> HASH sessionId -> podId; online = hash com algum campo
    private static final String PRESENCE = "messenger:presence:";
    // + podId -> SET dos users com sessão no pod, para limpar o pod se ele morrer
    private static final String POD_USERS = "messenger:pod-users:";
    private static final String EPOCH = "messenger:presence:epoch";
    private static final String VERSION = "messenger:presence:version";

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
