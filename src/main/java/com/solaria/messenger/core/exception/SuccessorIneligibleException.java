package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade | sem gestor sucessor elegivel */
public class SuccessorIneligibleException extends BusinessException {

    public SuccessorIneligibleException() {
        super("A operação de gestão não concluiu: SUCCESSOR_INELIGIBLE", HttpStatus.CONFLICT, "SUCCESSOR_INELIGIBLE");
    }
}
