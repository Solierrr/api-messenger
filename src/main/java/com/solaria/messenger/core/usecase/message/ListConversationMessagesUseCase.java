package com.solaria.messenger.core.usecase.message;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.mapper.MessageMapper;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

@Service
public class ListConversationMessagesUseCase {

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE_PAGE_SIZE = 500;
    private final MessageRepository messageRepository;
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;

    public ListConversationMessagesUseCase(MessageRepository messages, RbacAuthorizationService rbac,
            ConversationAccessHelper access) {
        this.messageRepository = messages;
        this.rbac = rbac;
        this.access = access;
    }

    /** Consulta o histórico autorizado sem reconstruir estado pessoal ou alterar mensagens. */
    public List<MessageResponseDTO> execute(String conversationId, Integer sinceSequence,
            Integer beforeSequence, Integer limit) {
        return access.withAccess(conversationId, rbac.currentUserId(),
                conversation -> getMessagesAuthorized(conversationId, sinceSequence, beforeSequence, limit));
    }

    /** Valida o cursor único e busca a página na ordem crescente de sequência. */
    private List<MessageResponseDTO> getMessagesAuthorized(String conversationId, Integer sinceSequence,
            Integer beforeSequence, Integer limit) {
        if (sinceSequence != null && beforeSequence != null) {
            throw new InvalidFieldException("Informe apenas um cursor: sinceSequence ou beforeSequence.");
        }
        if (sinceSequence != null && sinceSequence < 0) {
            throw new InvalidFieldException("sinceSequence deve ser maior ou igual a zero.");
        }
        if (beforeSequence != null && beforeSequence < 1) {
            throw new InvalidFieldException("beforeSequence deve ser maior ou igual a um.");
        }

        Limit pageLimit = Limit.of(clampMessagePageSize(limit));
        List<Message> messages;
        if (sinceSequence != null) {
            messages = messageRepository.findByConversationIdAndSequenceGreaterThanOrderBySequenceAscTimestampAsc(
                    conversationId, sinceSequence, pageLimit);
        } else {
            messages = beforeSequence == null
                    ? messageRepository.findByConversationIdOrderBySequenceDesc(conversationId, pageLimit)
                    : messageRepository.findByConversationIdAndSequenceLessThanOrderBySequenceDesc(
                            conversationId, beforeSequence, pageLimit);
            messages = messages.reversed();
        }

        return messages.stream()
                .map(MessageMapper::toResponse)
                .toList();
    }

    /** Limita a página entre 1 e 500, com 100 por padrão. */
    private int clampMessagePageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.clamp(requested, 1, MAX_MESSAGE_PAGE_SIZE);
    }
}
