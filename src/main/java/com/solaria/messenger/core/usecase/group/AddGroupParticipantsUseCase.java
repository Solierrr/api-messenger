package com.solaria.messenger.core.usecase.group;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.contract.response.RejectedParticipantDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.PersistenceUncertainException;
import com.solaria.messenger.core.helper.ConversationAdmissionHelper;
import com.solaria.messenger.core.helper.GroupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireNoNullParticipants;
import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

/**
 * Caso de uso que adiciona participantes a um grupo.
 *
 * <p>
 * Só o gestor ativo de um grupo ativo consegue adicionar. Os convidados precisam estar ativos e, em subgrupo, ser
 * membros da comunidade. A participação e os estados pessoais dos novos entram juntos, na mesma transação do
 * {@link ConversationAdmissionHelper}. O erro de persistência incerta indica que o resultado não é conhecido.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationAdmissionHelper} -> grava a admissão na transação.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o ator.</li>
 *     <li>{@link UserAccessHelper} -> confere os usuários ativos.</li>
 *     <li>{@link GroupHelper} -> valida o gestor e o grupo.</li>
 *     <li>{@link NotificationHelper} -> avisa os participantes.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, Set)} -> adiciona os participantes ao grupo.</li>
 * </ul>
 */
@Service
public class AddGroupParticipantsUseCase {

    private final ConversationAdmissionHelper admissions;
    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;
    private final GroupHelper groups;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public AddGroupParticipantsUseCase(ConversationAdmissionHelper admissions, RbacAuthorizationService rbac,
            UserAccessHelper users, GroupHelper groups, NotificationHelper notifications, ConversationMutationGuard guard) {
        this.admissions = admissions;
        this.rbac = rbac;
        this.users = users;
        this.groups = groups;
        this.notifications = notifications;
        this.guard = guard;
    }

    /**
     * Adiciona os participantes ao grupo e avisa a mudança.
     *
     * @param id identificador do grupo.
     * @param userIds usuários a adicionar.
     * @return grupo com a lista de participantes atualizada.
     * @throws PersistenceUncertainException caso a gravação falhe e não se saiba se ela ocorreu.
     */
    public ConversationResponseDTO execute(String id, Set<UUID> userIds) {
        boolean[] changed = { false };

        ConversationResponseDTO response = guard.execute(() -> {
            // só o gestor ativo de um grupo ativo adiciona
            UUID actor = rbac.currentUserId();
            users.requireActiveActor(actor);
            Conversation initial = groups.managedConversation(id);
            groups.requireManageable(initial, actor);

            // convidados sem nulos; quem não é da comunidade (se houver) ou está inativo fica de fora
            Set<UUID> requested = new LinkedHashSet<>(userIds);
            requireNoNullParticipants(requested);
            List<RejectedParticipantDTO> rejected = new ArrayList<>();
            ProjectCommunity community = groups.communityFor(initial);
            if (community != null) requested.stream().filter(user -> !community.isMember(user))
                    .forEach(user -> rejected.add(new RejectedParticipantDTO(user, "NOT_COMMUNITY_MEMBER")));
            rejected.forEach(user -> requested.remove(user.userId()));
            Set<UUID> additions = new LinkedHashSet<>(requested);
            additions.removeAll(initial.getParticipantIds());
            rejected.addAll(users.ineligibleUsers(additions));
            rejected.forEach(user -> requested.remove(user.userId()));
            if (requested.isEmpty()) throw new BusinessRuleException("Nenhum convidado pode entrar no grupo.");

            // grava na transação; erro técnico vira "persistência incerta"
            try {
                Conversation accepted = admissions.admitParticipants(id, initial.getVersion(), actor, requested);
                changed[0] = !initial.getParticipantIds().equals(accepted.getParticipantIds());
                ConversationResponseDTO result = toResponse(accepted);
                result.setRejectedParticipants(List.copyOf(rejected));
                return result;
            } catch (BusinessException | OptimisticLockingFailureException refused) {
                throw refused;
            } catch (TransactionException uncertain) {
                throw new PersistenceUncertainException("ADMISSION_PERSISTENCE_UNCERTAIN", id, uncertain);
            } catch (RuntimeException failure) {
                throw new PersistenceUncertainException("DEPENDENCY_UNAVAILABLE", id, failure);
            }
        });

        if (changed[0]) notifications.publishConversationChanged(id);
        return response;
    }
}
