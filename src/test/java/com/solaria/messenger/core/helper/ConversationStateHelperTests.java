package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.support.ConversationSyncClock;

/** Cobre a marca de saída: ela leva a versão do relógio e uma falha técnica nunca impede o aviso de remoção. */
class ConversationStateHelperTests {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ConversationUserStateRepository repository;
    private ConversationSyncClock clock;
    private ConversationStateHelper helper;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationUserStateRepository.class);
        clock = mock(ConversationSyncClock.class);
        helper = new ConversationStateHelper(repository, clock);
    }

    @Test
    void markLeftStoresTheClockVersion() {
        given(clock.nextVersion()).willReturn(321L);

        helper.markLeft(USER, "conv-1");

        verify(repository).markLeft("conv-1", USER, 321L);
    }

    @Test
    void markLeftFailureIsLoggedAndDoesNotReachTheRemovalFlow() {
        given(clock.nextVersion()).willReturn(321L);
        doThrow(new IllegalStateException("mongo fora")).when(repository).markLeft("conv-1", USER, 321L);

        assertThatCode(() -> helper.markLeft(USER, "conv-1")).doesNotThrowAnyException();

        given(clock.nextVersion()).willThrow(new IllegalStateException("relógio fora"));
        assertThatCode(() -> helper.markLeft(USER, "conv-2")).doesNotThrowAnyException();
        verify(repository, never()).markLeft(eq("conv-2"), any(), anyLong());
    }
}
