package com.solaria.messenger.core.port;

import java.util.Set;
import java.util.UUID;

import com.solaria.messenger.core.model.Message;

/**
 * Porta de saída para eventos de mensagem já persistida.
 *
 * <p>
 * O núcleo não conhece o transporte. Quem chama já decidiu e autorizou os destinatários. O evento vai ao canal de
 * eventos de cada um deles, inclusive do autor, para que os outros aparelhos dele também recebam. A implementação
 * entrega em tempo real e nunca propaga falha de entrega, que não desfaz a escrita.
 * </p>
 */
public interface MessageEventPublisher {

    /** Anuncia uma mensagem nova a cada destinatário. */
    void messageCreated(Message message, Set<UUID> recipients);

    /** Anuncia a edição confirmada de uma mensagem a cada destinatário. */
    void messageEdited(Message message, Set<UUID> recipients);

    /** Anuncia o tombstone confirmado de uma mensagem excluída a cada destinatário. */
    void messageDeleted(Message message, Set<UUID> recipients);
}
