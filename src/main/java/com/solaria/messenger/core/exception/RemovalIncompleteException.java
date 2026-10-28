package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;
 
/** Falha de gestão de conversa/comunidade | remoção de alguem de uma comunidade foi,
 * porém os subgrupos que ele participa não foi concluído */
public class RemovalIncompleteException extends BusinessException {

    public RemovalIncompleteException() {
        super("A operação de gestão não concluiu: REMOVAL_INCOMPLETE", HttpStatus.SERVICE_UNAVAILABLE, "REMOVAL_INCOMPLETE");
    }
}
