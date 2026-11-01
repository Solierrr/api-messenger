package com.solaria.messenger.core.port;

import java.util.UUID;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;

/**
 * Porta de saída para o estado das conversas de cada participante.
 *
 * <p>
 * O núcleo calcula e autoriza a projeção. A implementação só entrega ao destinatário e nunca propaga falha de entrega.
 * </p>
 */
public interface ConversationEventPublisher {

    /** Entrega a projeção atualizada da conversa somente ao destinatário informado. */
    void conversationUpdated(UUID recipient, ConversationListItemResponseDTO item);

    /** Avisa o destinatário que perdeu a participação, sem enviar roster, conteúdo ou preferências. */
    void conversationRemoved(UUID recipient, String conversationId, String syncEpoch, long syncVersion);
}
