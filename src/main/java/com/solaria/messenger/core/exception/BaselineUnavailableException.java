package com.solaria.messenger.core.exception;

/** O estado pessoal da conversa não tem baseline de admissão verificável | 422 */
public class BaselineUnavailableException extends BusinessRuleException {

    public static final String MESSAGE = "Estado pessoal indisponível: baseline de admissão não verificável";

    public BaselineUnavailableException() {
        super(MESSAGE);
    }
}
