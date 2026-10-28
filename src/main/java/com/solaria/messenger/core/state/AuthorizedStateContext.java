package com.solaria.messenger.core.state;

import java.util.UUID;

/** Contexto de autorização e limite persistido para alterar somente o estado próprio. */
public record AuthorizedStateContext(String conversationId, UUID actor, int persistedHeadSequence) {
}
