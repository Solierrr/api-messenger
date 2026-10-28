package com.solaria.messenger.core.state;


/** Resultado do PATCH pessoal, incluindo a distinção entre mudança e no-op. */
public record StateChange(OwnedState state, boolean changed) {
}
