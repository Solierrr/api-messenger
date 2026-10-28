package com.solaria.messenger.core.usecase.group;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.helper.GroupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

@Service
public class TransferGroupManagerUseCase {

    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;
    private final GroupHelper groups;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public TransferGroupManagerUseCase(RbacAuthorizationService rbac, UserAccessHelper users, GroupHelper groups,
            NotificationHelper notifications, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.users = users;
        this.groups = groups;
        this.notifications = notifications;
        this.guard = guard;
    }

    /** Passa a gestão para o sucessor; o antigo gestor perde a autoridade na hora */
    public ConversationResponseDTO execute(String id, UUID successorUserId) {
        ConversationResponseDTO response = guard.execute(() -> {
            // ator ativo, gestor do grupo, e sucessor válido e ativo
            UUID actor = rbac.currentUserId();
            users.requireActiveActor(actor);
            Conversation initial = groups.managedConversation(id);
            groups.requireManageable(initial, actor);
            groups.validateSuccessor(initial, actor, successorUserId);
            users.requireSuccessorsActive(Set.of(successorUserId));

            // grava com CAS, revalidando na versão mais recente
            return toResponse(groups.mutateGroup(initial, latest -> {
                groups.requireManageable(latest, actor);
                groups.validateSuccessor(latest, actor, successorUserId);
                return groups.writeManager(latest, successorUserId);
            }));
        });

        notifications.publishConversationChanged(id);
        return response;
    }
}
