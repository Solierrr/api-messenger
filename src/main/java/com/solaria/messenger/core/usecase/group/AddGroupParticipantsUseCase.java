package com.solaria.messenger.core.usecase.group;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
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

    /** Adiciona participantes; elegibilidade é checada antes, e participação + estados são gravados juntos */
    public ConversationResponseDTO execute(String id, Set<UUID> userIds) {
        boolean[] changed = { false };

        ConversationResponseDTO response = guard.execute(() -> {
            // só o gestor ativo de um grupo ativo adiciona
            UUID actor = rbac.currentUserId();
            users.requireActiveActor(actor);
            Conversation initial = groups.managedConversation(id);
            groups.requireManageable(initial, actor);

            // convidados sem nulos, membros da comunidade (se houver), e os novos precisam estar ativos
            Set<UUID> requested = new LinkedHashSet<>(userIds);
            requireNoNullParticipants(requested);
            validateCommunityMembers(initial, requested);
            Set<UUID> additions = new LinkedHashSet<>(requested);
            additions.removeAll(initial.getParticipantIds());
            users.requireActiveUsers(additions);

            // grava na transação; erro técnico vira "persistência incerta"
            try {
                Conversation accepted = admissions.admitParticipants(id, initial.getVersion(), actor, requested);
                changed[0] = !initial.getParticipantIds().equals(accepted.getParticipantIds());
                return toResponse(accepted);
            } catch (BusinessException | OptimisticLockingFailureException rejected) {
                throw rejected;
            } catch (TransactionException uncertain) {
                throw new PersistenceUncertainException("ADMISSION_PERSISTENCE_UNCERTAIN", id, uncertain);
            } catch (RuntimeException failure) {
                throw new PersistenceUncertainException("DEPENDENCY_UNAVAILABLE", id, failure);
            }
        });

        if (changed[0]) notifications.publishConversationChanged(id);
        return response;
    }

    /** Em subgrupo, todos os convidados precisam ser membros da comunidade */
    private void validateCommunityMembers(Conversation conversation, Set<UUID> users) {
        ProjectCommunity community = groups.communityFor(conversation);
        if (community != null && users.stream().anyMatch(user -> !community.isMember(user))) {
            throw new BusinessRuleException("Todos os participantes precisam ser membros da comunidade.");
        }
    }
}
