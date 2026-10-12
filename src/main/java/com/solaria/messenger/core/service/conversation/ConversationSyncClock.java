package com.solaria.messenger.core.service.conversation;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** Época e versão monotônica da sincronização de conversas, compartilhadas por REST e eventos. */
@Component
public class ConversationSyncClock {

    private final String epoch = UUID.randomUUID().toString();
    private final AtomicLong versions = new AtomicLong();

    /** Época desta instância; muda a cada reinício para invalidar versões antigas. */
    public String epoch() { return epoch; }

    /** Chamado dentro da fronteira local de projeção, antes de liberar a leitura para outra mutação. */
    public long nextVersion() { return versions.incrementAndGet(); }
}
