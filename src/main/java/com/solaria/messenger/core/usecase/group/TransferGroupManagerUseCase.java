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

/**
 * Caso de uso que transfere a gestão de um grupo para outro participante.
 *
 * <p>
 * O gestor atual perde a autoridade assim que a transferência é gravada. O sucessor passa pela validação do
 * {@link GroupHelper} e precisa estar ativo. A gravação usa CAS, e as regras são revalidadas na versão mais recente do
 * grupo.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RbacAuthorizationService} -> identifica o gestor atual.</li>
 *     <li>{@link UserAccessHelper} -> confere o gestor e o sucessor ativos.</li>
 *     <li>{@link GroupHelper} -> valida o gestor e grava o novo gestor com CAS.</li>
 *     <li>{@link NotificationHelper} -> avisa os clientes da mudança.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, UUID)} -> transfere a gestão para o sucessor.</li>
 * </ul>
 */
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

    /**
     * Transfere a gestão do grupo para o sucessor informado.
     *
     * @param id identificador do grupo.
     * @param successorUserId participante que passa a ser o gestor.
     * @return grupo com o novo gestor.
     */
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
