package com.solaria.messenger.core.usecase.message;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.helper.MessageMutationResult;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

@Service
public class DeleteMessageUseCase {

    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;

    public DeleteMessageUseCase(MessageWriteHelper writes, NotificationHelper notifications) {
        this.writes = writes;
        this.notifications = notifications;
    }

    /** Exclui uma mensagem própria; excluir de novo não grava nem avisa outra vez */
    public MessageResponseDTO execute(String messageId) {
        MessageMutationResult result = writes.mutate(messageId, null, true);
        if (result.changed()) notifications.notifyMessageDeleted(result.message());
        return toResponse(result.message());
    }
}
