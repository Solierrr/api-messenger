package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** Falha de gestão de conversa/comunidade | mensagem não pode ser editada ou removida por falha */
public class MessageMutationUncertainException extends BusinessException {

    public MessageMutationUncertainException() {
        super("A operação de gestão não concluiu: MESSAGE_MUTATION_UNCERTAIN", HttpStatus.SERVICE_UNAVAILABLE, "MESSAGE_MUTATION_UNCERTAIN");
    }
}
