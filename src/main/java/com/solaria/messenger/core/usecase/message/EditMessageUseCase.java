package com.solaria.messenger.core.usecase.message;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.MessageEditRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.MessageMutationResult;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

@Service
public class EditMessageUseCase {

    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;

    public EditMessageUseCase(MessageWriteHelper writes, NotificationHelper notifications) {
        this.writes = writes;
        this.notifications = notifications;
    }

    /** Troca o conteúdo de uma mensagem própria que ainda não foi excluída */
    public MessageResponseDTO execute(String messageId, MessageEditRequestDTO dto) {
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank() || dto.getContent().length() > 8000) {
            throw new InvalidFieldException("content deve conter entre 1 e 8000 caracteres e não pode ser branco.");
        }
        MessageMutationResult result = writes.mutate(messageId, dto.getContent(), false);
        if (result.changed()) notifications.notifyMessageEdited(result.message());
        return toResponse(result.message());
    }
}
