package com.solaria.messenger.core.port;

import com.solaria.messenger.core.model.Message;

/**
 * Porta de saída para eventos de mensagem já persistida. O núcleo não conhece o transporte:
 * a implementação entrega em tempo real e nunca propaga falha de entrega, que não desfaz a escrita.
 */
public interface MessageEventPublisher {

    /** Anuncia uma mensagem nova aos inscritos da conversa. */
    void messageCreated(Message message);

    /** Anuncia a edição confirmada de uma mensagem. */
    void messageEdited(Message message);

    /** Anuncia o tombstone confirmado de uma mensagem excluída. */
    void messageDeleted(Message message);
}
