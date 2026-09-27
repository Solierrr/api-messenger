package com.solaria.messenger.security;

/**
 * Excecao usada para falhas de autenticacao em frames STOMP
 * 
 * <p> não estende {@code BusinessException} poque ela é feita para requests http
 * O objetivo aqui é levar {@code RuntimeException} para fora de {@code preSend}
 * permitindo que o spring retorne um stomp ERROR e feche a conexão </p>
 */
public class StompAuthenticationException extends RuntimeException {

    public StompAuthenticationException(String message) {
        super(message);
    }

    public StompAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
