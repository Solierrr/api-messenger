package com.solaria.messenger.core.usecase.group;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.helper.GroupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.port.SessionRevoker;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.withoutUser;

@Service
public class RemoveGroupParticipantUseCase {

    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;
    private final SessionRevoker sessionRevoker;
    private final GroupHelper groups;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public RemoveGroupParticipantUseCase(RbacAuthorizationService rbac, UserAccessHelper users,
            SessionRevoker sessionRevoker, GroupHelper groups, NotificationHelper notifications,
            ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.users = users;
        this.sessionRevoker = sessionRevoker;
        this.groups = groups;
        this.notifications = notifications;
        this.guard = guard;
    }

    /** Remove um participante; sair por conta própria funciona até com o grupo arquivado */
    public void execute(String id, UUID userId, UUID successorUserId) {
        boolean[] changed = { false };
        try {
            guard.execute(() -> {
                UUID actor = rbac.currentUserId();
                users.requireActiveActor(actor);
                Conversation initial = groups.managedConversation(id);
                authorizeRemoval(initial, actor, userId);
                if (!initial.hasParticipant(userId)) return null;

                // quem sai sozinho e já não está na comunidade não indica sucessor
                ProjectCommunity parent = groups.communityFor(initial);
                UUID successor = actor.equals(userId) && parent != null && !parent.isMember(actor)
                        ? null : successorUserId;
                groups.validateRemoval(initial, actor, userId, successor);
                if (successor != null) users.requireSuccessorsActive(Set.of(successor));

                groups.mutateGroup(initial, latest -> {
                    authorizeRemoval(latest, actor, userId);
                    if (!latest.hasParticipant(userId)) return null;
                    groups.validateRemoval(latest, actor, userId, successor);

                    // tira o user; se ele era o gestor, o sucessor assume; grupo vazio fica sem gestor
                    Set<UUID> survivors = withoutUser(latest.getParticipantIds(), userId);
                    UUID manager = latest.getManagerUserId();
                    if (survivors.isEmpty()) manager = null;
                    else if (userId.equals(manager)) manager = successor;
                    return groups.writeParticipants(latest, survivors, manager);
                });

                // avisa quem saiu e derruba as sessões dele
                changed[0] = true;
                notifications.publishRemovedConversation(userId, id);
                sessionRevoker.revokeUser(userId);
                return null;
            });
        } finally {
            if (changed[0]) notifications.publishConversationChanged(id);
        }
    }

    /** Sair por conta própria só exige participar; remover outra pessoa exige gestão */
    private void authorizeRemoval(Conversation conversation, UUID actor, UUID target) {
        if (!actor.equals(target)) {
            groups.requireManageable(conversation, actor);
            return;
        }

        // gestor que já saiu da comunidade não pode sair do grupo sozinho (fica sem sucessor válido)
        if (!conversation.hasParticipant(actor)) return;
        ProjectCommunity community = groups.communityFor(conversation);
        if (community != null && !community.isMember(actor) && actor.equals(conversation.getManagerUserId())) {
            throw new GroupManagerUnavailableException();
        }
    }
}
