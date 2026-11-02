package com.solaria.messenger.core.usecase.media;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.MediaListItemResponseDTO;
import com.solaria.messenger.core.contract.response.MediaListResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.mapper.MessageMapper;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

/**
 * Caso de uso que lista as mídias ainda disponíveis para quem acabou de iniciar sessão e não tem nada no aparelho.
 *
 * <p>
 * Mostra só mensagens com arquivo não expirado, não excluídas, das conversas que o usuário pode ler (ou de uma só).
 * Vem da mais recente para a mais antiga, ordenada pela versão da última mudança, e o cursor é essa versão. Não consulta
 * o armazenamento: o prazo vem gravado na mensagem.
 * </p>
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationAccessHelper} -> conversas legíveis e participação.</li>
 *     <li>{@link MessageRepository} -> mensagens com arquivo vivo.</li>
 * </ul>
 */
@Service
public class ListAvailableMediaUseCase {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final MessageRepository messages;

    public ListAvailableMediaUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access,
            MessageRepository messages) {
        this.rbac = rbac;
        this.access = access;
        this.messages = messages;
    }

    /**
     * Lista uma página de mídias disponíveis.
     *
     * @param conversationId uma conversa, ou nulo para todas as que o usuário lê.
     * @param limit tamanho da página; sem valor usa 50, e fora de 1 a 100 é ajustado.
     * @param cursor valor devolvido na página anterior, ou nulo para começar.
     * @return página e cursor da seguinte.
     * @throws InvalidFieldException caso o cursor não seja o devolvido por uma página.
     */
    public MediaListResponseDTO execute(String conversationId, Integer limit, String cursor) {
        UUID actor = rbac.currentUserId();
        long beforeVersion = parseCursor(cursor);
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Set<String> conversations;
        if (conversationId == null) {
            conversations = access.readableConversationIds(actor);
        } else {
            access.requireParticipant(conversationId, actor);
            conversations = Set.of(conversationId);
        }
        if (conversations.isEmpty()) return new MediaListResponseDTO(List.of(), null);

        // uma a mais do que a página mostra diz se ainda há o que buscar
        List<Message> found = messages.findAvailableMedia(conversations, Instant.now(), beforeVersion, pageSize + 1);
        List<Message> page = found.stream().limit(pageSize).toList();
        String nextCursor = found.size() > pageSize ? String.valueOf(page.getLast().getChangeVersion()) : null;
        List<MediaListItemResponseDTO> items = page.stream()
                .map(message -> new MediaListItemResponseDTO(message.getId(), message.getConversationId(),
                        message.getSenderId(), message.getTimestamp(), MessageMapper.toAttachment(message.getAttachment())))
                .toList();
        return new MediaListResponseDTO(items, nextCursor);
    }

    private static long parseCursor(String cursor) {
        if (cursor == null) return Long.MAX_VALUE;
        try {
            long version = Long.parseLong(cursor);
            if (version < 0) throw new NumberFormatException(cursor);
            return version;
        } catch (NumberFormatException invalid) {
            throw new InvalidFieldException("cursor inválido.");
        }
    }
}
