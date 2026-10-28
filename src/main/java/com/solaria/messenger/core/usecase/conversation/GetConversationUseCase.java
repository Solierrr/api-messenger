package com.solaria.messenger.core.usecase.conversation;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

@Service
public class GetConversationUseCase {

    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;

    public GetConversationUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access) {
        this.rbac = rbac;
        this.access = access;
    }

    /** Busca a conversa depois de confirmar que o user ainda participa */
    public ConversationResponseDTO execute(String id) {
        return access.withAccess(id, rbac.currentUserId(), ConversationMapper::toResponse);
    }
}
