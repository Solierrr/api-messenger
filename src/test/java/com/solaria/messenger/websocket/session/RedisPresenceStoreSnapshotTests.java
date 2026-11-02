package com.solaria.messenger.websocket.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Cobre o retrato de presença em lote: o resultado do pipeline vira o mapa de online por user, e o Redis fora do ar
 * devolve {@code null} para o registro cair na visão do pod.
 */
class RedisPresenceStoreSnapshotTests {

    private static final UUID U1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID U2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisPresenceStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(values);
        given(values.get("messenger:presence:epoch")).willReturn("e1");
        given(values.increment("messenger:presence:version")).willReturn(7L);
        store = new RedisPresenceStore(redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pipelinedLengthsMapToOnlineFlags() {
        given(redis.executePipelined(any(RedisCallback.class))).willReturn(List.of(2L, 0L));

        RedisPresenceStore.Snapshot snapshot = store.snapshot(new LinkedHashSet<>(List.of(U1, U2)));

        assertThat(snapshot.epoch()).isEqualTo("e1");
        assertThat(snapshot.version()).isEqualTo(7L);
        assertThat(snapshot.online()).containsEntry(U1, true).containsEntry(U2, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureReturnsNull() {
        given(redis.executePipelined(any(RedisCallback.class))).willThrow(new IllegalStateException("redis"));

        assertThat(store.snapshot(Set.of(U1))).isNull();
    }

    @Test
    void emptyUserSetNeedsNoPipeline() {
        RedisPresenceStore.Snapshot snapshot = store.snapshot(Set.of());

        assertThat(snapshot.online()).isEmpty();
    }
}
