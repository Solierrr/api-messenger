package com.solaria.messenger.core.helper;

import com.solaria.messenger.core.model.Message;

/**
 * Resultado de uma edição ou exclusão de mensagem.
 *
 * <p>
 * Traz a mensagem depois da operação e se a gravação realmente aconteceu. Quando a mensagem já estava no estado
 * pedido, {@code changed} é falso e nada é avisado.
 * </p>
 *
 * @param message mensagem depois da operação.
 * @param changed {@code true} quando a gravação aconteceu nesta chamada.
 */
public record MessageMutationResult(Message message, boolean changed) { }
