package com.solaria.messenger.core.mapper;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
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
                .timestamp(message.getTimestamp())
                .sequence(message.getSequence())
                .changeVersion(message.getChangeVersion())
                .build();
    }
}
