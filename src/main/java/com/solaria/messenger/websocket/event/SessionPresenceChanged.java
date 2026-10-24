package com.solaria.messenger.websocket.event;

import java.util.UUID;

/** Transição de presença produzida pela primeira ou pela última sessão admitida. */
public record SessionPresenceChanged(UUID userId, boolean online, long presenceVersion) {
}
