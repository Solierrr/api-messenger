package com.solaria.messenger.websocket.dto.response;

import java.util.Map;
import java.util.UUID;

/**  snapshot da prensença em x momento */
public record PresenceSnapshotDTO(String subscriptionId, String presenceEpoch, long presenceVersion,
        Map<UUID, Boolean> users) {
    public PresenceSnapshotDTO { users = Map.copyOf(users); }
}
