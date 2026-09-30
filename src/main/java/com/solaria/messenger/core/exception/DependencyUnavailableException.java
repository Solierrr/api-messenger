package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;


/**
 * Lançada quando uma dependência externa ou técnica está indisponível ou responde de forma inválida
 */
public class DependencyUnavailableException extends BusinessException {

    public DependencyUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
        initCause(cause);
    }
}
