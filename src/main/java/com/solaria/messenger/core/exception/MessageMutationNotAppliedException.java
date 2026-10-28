package com.solaria.messenger.core.exception;

/**
 * A escrita condicional da mensagem não foi aplicada por uma alteração concorrente 
 */
public class MessageMutationNotAppliedException extends ConcurrentUpdateException {

    public MessageMutationNotAppliedException() {
        super("A escrita da mensagem não foi aplicada por uma alteração concorrente.");
    }
}
