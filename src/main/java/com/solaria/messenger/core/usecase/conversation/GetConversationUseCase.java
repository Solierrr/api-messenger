package com.solaria.messenger.core.usecase.conversation;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

/**
 * Caso de uso que busca uma conversa para o user atual.
 *
 * <p>
 * Devolve a conversa só depois de confirmar que o user ainda participa dela. A checagem fica com o
 * {@link ConversationAccessHelper}, que também cobre a comunidade do subgrupo.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link ConversationAccessHelper} -> confere a participação e devolve a conversa.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> busca a conversa pelo identificador.</li>
 * </ul>
 */
@Service
public class GetConversationUseCase {

    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;

    public GetConversationUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access) {
        this.rbac = rbac;
        this.access = access;
    }

    /**
     * Busca a conversa depois de confirmar que o user ainda participa.
     *
     * @param id identificador da conversa.
     * @return dados da conversa no formato de resposta.
     */
    public ConversationResponseDTO execute(String id) {
        return access.withAccess(id, rbac.currentUserId(), ConversationMapper::toResponse);
    }
}
