package com.solaria.messenger.core.usecase.message;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.MessageEditRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.MessageMutationResult;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Caso de uso que edita o conteúdo de uma mensagem do próprio user.
 *
 * <p>
 * O conteúdo precisa ter de 1 a 8000 caracteres e não pode ser só espaços. Uma mensagem excluída não pode ser
 * editada. A gravação fica com o {@link MessageWriteHelper}, e os clientes recebem o aviso de edição.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MessageWriteHelper} -> confere a autoria e grava a edição.</li>
 *     <li>{@link NotificationHelper} -> avisa a edição.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, MessageEditRequestDTO)} -> troca o conteúdo da mensagem.</li>
 * </ul>
 */
@Service
public class EditMessageUseCase {

    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;

    public EditMessageUseCase(MessageWriteHelper writes, NotificationHelper notifications) {
        this.writes = writes;
        this.notifications = notifications;
    }

    /**
     * Troca o conteúdo de uma mensagem própria que ainda não foi excluída.
     *
     * @param messageId identificador da mensagem.
     * @param dto novo conteúdo da mensagem.
     * @return mensagem com o conteúdo atualizado.
     * @throws InvalidFieldException caso o conteúdo seja nulo, branco ou tenha mais de 8000 caracteres.
     */
    public MessageResponseDTO execute(String messageId, MessageEditRequestDTO dto) {
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank() || dto.getContent().length() > 8000) {
            throw new InvalidFieldException("content deve conter entre 1 e 8000 caracteres e não pode ser branco.");
        }
        MessageMutationResult result = writes.mutate(messageId, dto.getContent(), false);
        if (result.changed()) notifications.notifyMessageEdited(result.message());
        return toResponse(result.message());
    }
}
