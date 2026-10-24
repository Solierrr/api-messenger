package com.solaria.messenger.core.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lançada quando o usuário autenticado não tem permissão para a operação solicitada.
 *=
 */
public class UnauthorizedAccessException extends BusinessException {

    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS");
    }

    public UnauthorizedAccessException(String message, Map<String, Object> properties) {
        super(message, HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", properties);
    }
}
