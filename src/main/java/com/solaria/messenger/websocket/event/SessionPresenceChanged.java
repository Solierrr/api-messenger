package com.solaria.messenger.websocket.event;

import java.util.UUID;

/**
 * Presença do user depois que a primeira ou a última sessão deste pod mudou
 */
public record SessionPresenceChanged(UUID userId, boolean online, String presenceEpoch, long presenceVersion) {
}
