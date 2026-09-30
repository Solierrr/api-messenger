package com.solaria.messenger.core.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lançada quando uma regra de negócio é violada
 */
public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION");
    }

    public BusinessRuleException(String message, Map<String, Object> properties) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION", properties);
    }
}
