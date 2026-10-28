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

/**
 * Caso de uso que lista o histórico de mensagens de uma conversa.
 *
 * <p>
 * Aceita um cursor por vez. {@code sinceSequence} traz as mensagens depois de uma sequência. {@code beforeSequence}
 * traz as anteriores a ela. Sem cursor, traz as mensagens mais recentes. A página vai de 1 a 500 mensagens, com 100 por
 * padrão. A consulta não altera mensagens nem estado pessoal.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MessageRepository} -> busca as páginas de mensagens.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link ConversationAccessHelper} -> confere a participação antes da leitura.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, Integer, Integer, Integer)} -> consulta a página de mensagens.</li>
 * </ul>
 */
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

    /**
     * Consulta o histórico autorizado da conversa.
     *
     * @param conversationId identificador da conversa.
     * @param sinceSequence sequência a partir da qual buscar. Não pode vir junto com beforeSequence.
     * @param beforeSequence sequência limite. Traz as mensagens anteriores a ela.
     * @param limit tamanho da página. Sem valor, usa 100. Valores fora de 1 a 500 são ajustados para o limite.
     * @return mensagens em ordem crescente de sequência.
     * @throws InvalidFieldException caso os cursores sejam inválidos ou venham juntos.
     */
    public List<MessageResponseDTO> execute(String conversationId, Integer sinceSequence,
            Integer beforeSequence, Integer limit) {
        return access.withAccess(conversationId, rbac.currentUserId(),
                conversation -> getMessagesAuthorized(conversationId, sinceSequence, beforeSequence, limit));
    }

    /** Valida o cursor único e busca a página na ordem crescente de sequência */
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

    /** Limita a página entre 1 e 500, com 100 por padrão */
    private int clampMessagePageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.clamp(requested, 1, MAX_MESSAGE_PAGE_SIZE);
    }
}
