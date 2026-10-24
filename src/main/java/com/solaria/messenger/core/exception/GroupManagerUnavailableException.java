package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade | grupo/comunidade sem gestor */
public class GroupManagerUnavailableException extends BusinessException {

    public GroupManagerUnavailableException() {
        super("A operação de gestão não concluiu: GROUP_MANAGER_UNAVAILABLE", HttpStatus.CONFLICT, "GROUP_MANAGER_UNAVAILABLE");
    }
}
