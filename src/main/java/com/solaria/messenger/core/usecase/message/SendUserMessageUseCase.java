package com.solaria.messenger.core.usecase.message;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.AttachmentHelper;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.OfflineMessageNotifier;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Caso de uso que envia uma mensagem do user em uma conversa.
 *
 * <p>
 * Confere se a conversa está ativa, se o tipo da mensagem combina com o tipo da conversa e se a comunidade está
 * ativa. Só depois disso grava. Cada chamada grava uma mensagem nova, sem reaproveitar outra. Mensagens do tipo
 * {@code CHATBOT_TO_USER} só podem vir do pipeline de LLM.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RbacAuthorizationService} -> identifica o remetente.</li>
 *     <li>{@link ConversationAccessHelper} -> confere a participação e a comunidade.</li>
 *     <li>{@link MessageWriteHelper} -> grava a mensagem e atualiza a última interação.</li>
 *     <li>{@link NotificationHelper} -> avisa a criação da mensagem.</li>
 *     <li>{@link OfflineMessageNotifier} -> agenda o push a quem está offline.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(MessageRequestDTO)} -> grava a mensagem do user.</li>
 * </ul>
 */
@Service
public class SendUserMessageUseCase {

    private static final String ROLE_USER = "user";
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final MessageWriteHelper writes;
    private final AttachmentHelper attachments;
    private final NotificationHelper notifications;
    private final OfflineMessageNotifier offlineNotifier;
    private final ConversationMutationGuard guard;

    public SendUserMessageUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access,
            MessageWriteHelper writes, AttachmentHelper attachments, NotificationHelper notifications,
            OfflineMessageNotifier offlineNotifier, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.access = access;
        this.writes = writes;
        this.attachments = attachments;
        this.notifications = notifications;
        this.offlineNotifier = offlineNotifier;
        this.guard = guard;
    }

    /**
     * Grava a mensagem do user e avisa a mudança.
     *
     * @param dto conteúdo, tipo da mensagem, identificador da conversa e clientMessageId opcional.
     * @return mensagem gravada.
     * @throws InvalidFieldException caso o tipo seja {@code CHATBOT_TO_USER}, o clientMessageId não seja um UUID
     *     canônico, ou o tipo não combine com a conversa.
     */
    public MessageResponseDTO execute(MessageRequestDTO dto) {
        UUID clientMessageId = parseClientMessageId(dto.getClientMessageId());
        requireTextOrAttachment(dto);
        if (dto.getMessageType() == MessageType.CHATBOT_TO_USER) {
            throw new InvalidFieldException(
                    "messageType CHATBOT_TO_USER só pode ser publicado pelo pipeline de LLM (POST /internal/messages).");
        }

        // a conferência do arquivo consulta o armazenamento (rede), então fica fora do guard, que serializa os envios da réplica
        Attachment attachment = dto.getAttachment() == null ? null
                : attachments.build(dto.getConversationId(), rbac.currentUserId(), dto.getAttachment());
        Message saved = guard.execute(() -> access.withAccess(dto.getConversationId(), rbac.currentUserId(),
                conversation -> sendAuthorized(conversation, dto, clientMessageId, attachment)));

        notifications.notifyMessageCreated(saved);
        offlineNotifier.notifyOffline(saved);
        return toResponse(saved);
    }

    /** Confere conversa ativa, tipo da mensagem e comunidade ativa, e grava */
    private Message sendAuthorized(Conversation conversation, MessageRequestDTO dto, UUID clientMessageId,
            Attachment attachment) {
        requireMessageTypeMatchesConversation(dto.getMessageType(), conversation.getConversationType());
        if (attachment != null && conversation.getConversationType() == ConversationType.CHAT_BOT) {
            throw new BusinessRuleException("A conversa com o assistente não aceita anexos.");
        }
        access.requireCommunityActive(conversation);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(rbac.currentUserId());
        message.setClientMessageId(clientMessageId);
        message.setRole(ROLE_USER);
        message.setMessageType(dto.getMessageType());
        message.setContent(dto.getContent() == null ? "" : dto.getContent());
        message.setAttachment(attachment);
        message.setTimestamp(Instant.now());
        return writes.appendAndTouch(conversation, message);
    }

    /** Sem anexo, o texto é obrigatório; com anexo, é a legenda e pode faltar. */
    private void requireTextOrAttachment(MessageRequestDTO dto) {
        boolean hasText = dto.getContent() != null && !dto.getContent().isBlank();
        if (!hasText && dto.getAttachment() == null) {
            throw new InvalidFieldException("content é obrigatório quando a mensagem não tem anexo.");
        }
    }

    /** clientMessageId é opcional, mas se vier precisa ser um UUID canônico */
    private UUID parseClientMessageId(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new InvalidFieldException("clientMessageId deve ser um UUID canônico quando informado.");
        }
        return UUID.fromString(value);
    }

    /**
     * O tipo da mensagem precisa combinar com a conversa: GROUP -> USER_TO_GROUP, DIRECT -> USER_TO_USER, CHAT_BOT ->
     * USER_TO_CHATBOT
     */
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
