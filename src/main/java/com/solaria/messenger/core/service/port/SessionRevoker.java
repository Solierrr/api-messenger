package com.solaria.messenger.core.service.port;

import java.util.UUID;

/** Porta de saída para encerrar as conexões em tempo real de quem perdeu uma participação. */
public interface SessionRevoker {

    /** A reconexão continua permitida; cada recurso segue sujeito à autorização atual no Mongo. */
    void revokeUser(UUID userId);
}
