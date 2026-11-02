package com.solaria.messenger.core.helper;

import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Message;

/**
 * Resultado de uma edição ou exclusão de mensagem.
 *
 * <p>
 * Traz a mensagem depois da operação e se a gravação realmente aconteceu. Quando a mensagem já estava no estado
 * pedido, {@code changed} é falso e nada é avisado. Numa exclusão que gravou, {@code removedAttachment} é o anexo que a
 * mensagem tinha (a mensagem devolvida já vem sem ele), para o arquivo poder ser apagado também.
 * </p>
 *
 * @param message mensagem depois da operação.
 * @param changed {@code true} quando a gravação aconteceu nesta chamada.
 * @param removedAttachment anexo retirado por esta exclusão, ou nulo.
 */
public record MessageMutationResult(Message message, boolean changed, Attachment removedAttachment) {

    public MessageMutationResult(Message message, boolean changed) {
        this(message, changed, null);
    }
}
