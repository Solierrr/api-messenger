package com.solaria.messenger.core.usecase.message;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.helper.MessageMutationResult;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Caso de uso que exclui uma mensagem do próprio user.
 *
 * <p>
 * A exclusão é feita pelo {@link MessageWriteHelper}, que confere a autoria e grava com CAS. Quando algo muda, os
 * clientes recebem o aviso de exclusão. Excluir uma mensagem que já está excluída não grava nem avisa de novo.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MessageWriteHelper} -> confere a autoria e grava a exclusão.</li>
 *     <li>{@link NotificationHelper} -> avisa a exclusão.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> exclui a mensagem informada.</li>
 * </ul>
 */
@Service
public class DeleteMessageUseCase {

    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;

    public DeleteMessageUseCase(MessageWriteHelper writes, NotificationHelper notifications) {
        this.writes = writes;
        this.notifications = notifications;
    }

    /**
     * Exclui a mensagem informada.
     *
     * @param messageId identificador da mensagem.
     * @return mensagem depois da exclusão, sem conteúdo.
     */
    public MessageResponseDTO execute(String messageId) {
        MessageMutationResult result = writes.mutate(messageId, null, true);
        if (result.changed()) notifications.notifyMessageDeleted(result.message());
        return toResponse(result.message());
    }
}
