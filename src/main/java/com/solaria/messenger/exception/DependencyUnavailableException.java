package com.solaria.messenger.exception;

import org.springframework.http.HttpStatus;


public class DependencyUnavailableException extends BusinessException {

    public DependencyUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE");
        initCause(cause);
    }
}
