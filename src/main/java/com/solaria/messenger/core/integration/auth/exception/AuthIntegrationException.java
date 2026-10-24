package com.solaria.messenger.core.integration.auth.exception;

/**
 * Lançada quando a chamada ao endpoint {@code POST /auth/login} da api-auth falha
 *
 * <p>Preserva a exceção HTTP original como causa, para diagnóstico.</p>
 */
public class AuthIntegrationException extends RuntimeException {
    public AuthIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
