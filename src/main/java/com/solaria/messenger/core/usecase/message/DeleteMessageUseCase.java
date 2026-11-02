package com.solaria.messenger.core.usecase.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.helper.MessageMutationResult;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.port.MediaStore;
import com.solaria.messenger.core.repository.MessageRepository;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Caso de uso que exclui uma mensagem do próprio user.
 *
 * <p>
 * A exclusão é feita pelo {@link MessageWriteHelper}, que confere a autoria e grava com CAS. Quando algo muda, os
 * clientes recebem o aviso de exclusão. Excluir uma mensagem que já está excluída não grava nem avisa de novo. Se a
 * mensagem tinha um arquivo, ele é apagado do armazenamento depois do aviso; a falha só gera log, porque o prazo do
 * bucket apaga o arquivo de qualquer jeito.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MessageWriteHelper} -> confere a autoria e grava a exclusão.</li>
 *     <li>{@link NotificationHelper} -> avisa a exclusão.</li>
 *     <li>{@link MediaStore} -> apaga o arquivo do anexo.</li>
 *     <li>{@link MessageRepository} -> confere se outra mensagem ainda cita o mesmo arquivo.</li>
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

    private static final Logger log = LoggerFactory.getLogger(DeleteMessageUseCase.class);

    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;
    private final MediaStore store;
    private final MessageRepository messages;

    public DeleteMessageUseCase(MessageWriteHelper writes, NotificationHelper notifications, MediaStore store,
            MessageRepository messages) {
        this.writes = writes;
        this.notifications = notifications;
        this.store = store;
        this.messages = messages;
    }

    public MessageResponseDTO execute(String messageId) {
        MessageMutationResult result = writes.mutate(messageId, null, true);
        if (result.changed()) {
            notifications.notifyMessageDeleted(result.message());
            dropFile(result);
        }
        return toResponse(result.message());
    }

    /** Apaga o arquivo do anexo excluído; localização e texto não têm arquivo. */
    private void dropFile(MessageMutationResult result) {
        if (result.removedAttachment() == null || result.removedAttachment().getMediaId() == null) return;
        String mediaId = result.removedAttachment().getMediaId();
        try {
            // um reenvio pode ter gravado outra mensagem com o mesmo arquivo; enquanto ela existir, o arquivo fica
            if (messages.existsByAttachmentMediaIdAndDeletedFalse(mediaId)) return;
            store.delete(mediaId);
        } catch (RuntimeException failure) {
            log.warn("Falha ao apagar o arquivo da mensagem {}; o prazo do bucket o remove", result.message().getId(), failure);
        }
    }
}
