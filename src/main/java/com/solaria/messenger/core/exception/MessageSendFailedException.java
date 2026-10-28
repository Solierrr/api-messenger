package com.solaria.messenger.core.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * O envio de uma mensagem não inseriu ou não confirmou a inserção
 */
public class MessageSendFailedException extends BusinessException {

    public MessageSendFailedException(String message, HttpStatus status, String errorCode, Map<String, Object> properties) {
        super(message, status, errorCode, properties);
    }
}
