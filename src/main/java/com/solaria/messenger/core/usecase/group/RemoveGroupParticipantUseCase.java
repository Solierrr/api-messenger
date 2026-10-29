package com.solaria.messenger.core.usecase.group;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.helper.ConversationStateHelper;
import com.solaria.messenger.core.helper.GroupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.port.SessionRevoker;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.withoutUser;

/**
 * Caso de uso que remove um participante de um grupo.
 *
 * <p>
 * Remover outra pessoa exige ser o gestor do grupo. Sair por conta própria exige só participar, mesmo com o grupo
 * arquivado. Quando o gestor sai, o sucessor informado assume a gestão. Um grupo que fica vazio fica sem gestor. Depois
 * da remoção, o estado pessoal do user removido recebe a versão da saída, ele recebe o aviso e suas sessões são
 * derrubadas.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RbacAuthorizationService} -> identifica o ator.</li>
 *     <li>{@link UserAccessHelper} -> confere o ator e o sucessor.</li>
 *     <li>{@link SessionRevoker} -> derruba as sessões do user removido.</li>
 *     <li>{@link ConversationStateHelper} -> marca a versão em que o user perdeu a conversa.</li>
 *     <li>{@link GroupHelper} -> valida a remoção e grava os participantes.</li>
 *     <li>{@link NotificationHelper} -> avisa o user removido e os clientes.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, UUID, UUID)} -> remove o participante do grupo.</li>
 * </ul>
 */
@Service
public class RemoveGroupParticipantUseCase {

    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;
    private final SessionRevoker sessionRevoker;
    private final ConversationStateHelper states;
    private final GroupHelper groups;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public RemoveGroupParticipantUseCase(RbacAuthorizationService rbac, UserAccessHelper users,
            SessionRevoker sessionRevoker, ConversationStateHelper states, GroupHelper groups,
            NotificationHelper notifications, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.users = users;
        this.sessionRevoker = sessionRevoker;
        this.states = states;
        this.groups = groups;
        this.notifications = notifications;
        this.guard = guard;
    }

    /**
     * Remove um participante do grupo.
     *
     * @param id identificador do grupo.
     * @param userId participante a remover.
     * @param successorUserId participante que assume a gestão, quando o gestor sai.
     * @throws GroupManagerUnavailableException caso o gestor, já fora da comunidade, tente sair sozinho do grupo.
     */
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

                // marca a versão da saída, avisa quem saiu e derruba as sessões dele
                changed[0] = true;
                states.markLeft(userId, id);
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
