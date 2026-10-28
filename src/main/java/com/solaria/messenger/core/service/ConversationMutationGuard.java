package com.solaria.messenger.core.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/** Fronteira única de mutação/leitura na JVM, compartilhada por mensagens, grupos e comunidades. */
@Component
public class ConversationMutationGuard {
    private final Object monitor = new Object();

    /** Serializa apenas o trecho local de domínio; não mantém estado durável nem coordena outras instâncias. */
    public <T> T execute(Supplier<T> operation) {
        synchronized (monitor) { return operation.get(); }
    }

    /** Indica se a thread atual já está dentro da fronteira local. */
    public boolean isHeldByCurrentThread() { return Thread.holdsLock(monitor); }
}
