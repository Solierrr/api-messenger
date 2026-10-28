package com.solaria.messenger.core.usecase.message;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireConversationActive;
import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

@Service
public class SendUserMessageUseCase {

    private static final String ROLE_USER = "user";
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public SendUserMessageUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access,
            MessageWriteHelper writes, NotificationHelper notifications, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.access = access;
        this.writes = writes;
        this.notifications = notifications;
        this.guard = guard;
    }

    /** Grava a mensagem do user; cada chamada é um insert novo, nunca reaproveita um anterior */
    public MessageResponseDTO execute(MessageRequestDTO dto) {
        UUID clientMessageId = parseClientMessageId(dto.getClientMessageId());
        if (dto.getMessageType() == MessageType.CHATBOT_TO_USER) {
            throw new InvalidFieldException(
                    "messageType CHATBOT_TO_USER só pode ser publicado pelo pipeline de LLM (POST /internal/messages).");
        }

        Message saved = guard.execute(() -> access.withAccess(dto.getConversationId(), rbac.currentUserId(),
                conversation -> sendAuthorized(conversation, dto, clientMessageId)));

        notifications.notifyMessageCreated(saved);
        return toResponse(saved);
    }

    /** Confere conversa ativa, tipo da mensagem e comunidade ativa, e grava */
    private Message sendAuthorized(Conversation conversation, MessageRequestDTO dto, UUID clientMessageId) {
        requireConversationActive(conversation);
        requireMessageTypeMatchesConversation(dto.getMessageType(), conversation.getConversationType());
        access.requireCommunityActive(conversation);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(rbac.currentUserId());
        message.setClientMessageId(clientMessageId);
        message.setRole(ROLE_USER);
        message.setMessageType(dto.getMessageType());
        message.setContent(dto.getContent());
        message.setTimestamp(Instant.now());
        return writes.appendAndTouch(conversation, message);
    }

    /** clientMessageId é opcional, mas se vier precisa ser um UUID canônico */
    private UUID parseClientMessageId(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new InvalidFieldException("clientMessageId deve ser um UUID canônico quando informado.");
        }
        return UUID.fromString(value);
    }

    /** O tipo da mensagem precisa combinar com a conversa: GROUP -> USER_TO_GROUP, DIRECT -> USER_TO_USER, CHAT_BOT -> USER_TO_CHATBOT */
    private void requireMessageTypeMatchesConversation(MessageType messageType, ConversationType conversationType) {
        MessageType expected = switch (conversationType) {
            case GROUP -> MessageType.USER_TO_GROUP;
            case DIRECT -> MessageType.USER_TO_USER;
            case CHAT_BOT -> MessageType.USER_TO_CHATBOT;
        };
        if (messageType != expected) {
            throw new InvalidFieldException(
                    "messageType " + messageType + " não é válido para uma conversa do tipo " + conversationType + ".");
        }
    }
}
