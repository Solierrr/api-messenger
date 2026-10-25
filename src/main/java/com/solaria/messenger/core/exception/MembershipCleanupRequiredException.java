package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade | impede que alguém entre novamente em uma comunidade se ainda não foi exluído de algum subgrupo */
public class MembershipCleanupRequiredException extends BusinessException {

    public MembershipCleanupRequiredException() {
        super("A operação de gestão não concluiu: MEMBERSHIP_CLEANUP_REQUIRED", HttpStatus.CONFLICT, "MEMBERSHIP_CLEANUP_REQUIRED");
    }
}
