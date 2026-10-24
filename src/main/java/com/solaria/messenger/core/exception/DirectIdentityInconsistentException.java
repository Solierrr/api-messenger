package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade | par de usuarios na conversa inconsistente */
public class DirectIdentityInconsistentException extends BusinessException {

    public DirectIdentityInconsistentException() {
        super("A operação de gestão não concluiu: DIRECT_IDENTITY_INCONSISTENT", HttpStatus.CONFLICT, "DIRECT_IDENTITY_INCONSISTENT");
    }
}
