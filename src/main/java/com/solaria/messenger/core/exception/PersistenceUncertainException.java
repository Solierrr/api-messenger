package com.solaria.messenger.core.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * A persistência de uma conversa não pôde ser confirmada 
 */
public class PersistenceUncertainException extends BusinessException {

    private static final Logger log = LoggerFactory.getLogger(PersistenceUncertainException.class);

    public PersistenceUncertainException(String code, String conversationId, RuntimeException cause) {
        super("Não foi possível confirmar a operação. Consulte a conversa antes de repetir deliberadamente.",
                HttpStatus.SERVICE_UNAVAILABLE, code, Map.of("conversationId", conversationId));
        log.error("Persistência de conversa não confirmada (conversationId={}, code={})", conversationId, code, cause);
        addSuppressed(cause);
    }
}
