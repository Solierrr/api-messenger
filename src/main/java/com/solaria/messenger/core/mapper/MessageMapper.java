package com.solaria.messenger.core.mapper;

import com.solaria.messenger.core.contract.response.AttachmentResponseDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Message;

/** Mapeamento único da entidade persistida para o contrato público, sem consultas ou autorização. */
public final class MessageMapper {
    private MessageMapper() { }

    /** Preserva a mesma identidade, sequência, correlação e representação de edição/exclusão.[] */
    public static MessageResponseDTO toResponse(Message message) {
        return MessageResponseDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .clientMessageId(message.getClientMessageId())
                .edited(message.isEdited())
                .deleted(message.isDeleted())
                .role(message.getRole())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .attachment(toAttachment(message.getAttachment()))
                .timestamp(message.getTimestamp())
                .sequence(message.getSequence())
                .changeVersion(message.getChangeVersion())
                .build();
    }

    /** Descrição pública do anexo; o caminho da retirada só existe quando há arquivo. */
    public static AttachmentResponseDTO toAttachment(Attachment attachment) {
        if (attachment == null) return null;
        // o caminho acompanha a rota GET /messaging/media/{mediaId}/content (MediaController)
        String downloadPath = attachment.getMediaId() == null ? null : "/messaging/media/" + attachment.getMediaId() + "/content";
        return new AttachmentResponseDTO(attachment.getType(), attachment.getMediaId(), attachment.getMimeType(),
                attachment.getSizeBytes(), attachment.getName(), attachment.getDurationMs(), attachment.getWidth(),
                attachment.getHeight(), attachment.getClientPreview(), attachment.getExpiresAt(), downloadPath,
                attachment.getLatitude(), attachment.getLongitude(), attachment.getLabel());
    }
}
