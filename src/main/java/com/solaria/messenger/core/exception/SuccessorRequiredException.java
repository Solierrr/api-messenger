package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade | gestor sucessor requirido */
public class SuccessorRequiredException extends BusinessException {

    public SuccessorRequiredException() {
        super("A operação de gestão não concluiu: SUCCESSOR_REQUIRED", HttpStatus.CONFLICT, "SUCCESSOR_REQUIRED");
    }
}
