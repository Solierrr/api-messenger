package com.solaria.messenger.core.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lançada ao tentar criar um recurso que já existe
 *
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String message, Map<String, Object> properties) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", properties);
    }
}
