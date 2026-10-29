package com.solaria.messenger.core.service.port;

import java.util.UUID;

/** Porta de saída para saber se um usuário está online em alguma instância. */
public interface UserPresence {

    /** Sem a presença compartilhada, vale a visão da instância local. */
    boolean isOnline(UUID userId);
}
