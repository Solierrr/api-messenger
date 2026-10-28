package com.solaria.messenger.core.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Superclasse abstrata de todas as exceções criadas
 */

public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final transient Map<String, Object> properties;

    protected BusinessException(String message, HttpStatus status, String errorCode) {
        this(message, status, errorCode, Map.of());
    }

    protected BusinessException(String message, HttpStatus status, String errorCode,
                                Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.properties = (properties == null) ? Map.of() : Map.copyOf(properties);
    }

    /** Status HTTP correspondente ao erro */
    public HttpStatus getStatus() {
        return status;
    }

    /** Código estável do erro, usado pelos clientes REST e STOMP */
    public String getErrorCode() {
        return errorCode;
    }

    /** Propriedades extras do erro, como a correlação {@code clientMessageId} */
    public Map<String, Object> getProperties() {
        return properties;
    }
}
