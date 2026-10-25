package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade por concorrencia */
public class ConcurrentUpdateException extends BusinessException {

    public ConcurrentUpdateException() {
        this("A operação de gestão não concluiu: CONCURRENT_MODIFICATION");
    }

    public ConcurrentUpdateException(String message) {
        super(message, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }
}
