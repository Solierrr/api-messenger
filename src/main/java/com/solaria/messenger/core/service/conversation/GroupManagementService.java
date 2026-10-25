package com.solaria.messenger.core.service.conversation;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.PersistenceUncertainException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.SuccessorIneligibleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.core.service.port.SessionRevoker;

import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

/**
 * Service de gestão de grupo
 *
 * <p>
 * Adiciona e remove participantes e troca o gestor. Toda escrita confere a permissão atual, grava com CAS
 * e, quando alguém sai, derruba as sessões dele.
 * </p>
 * <ul>
 *     <li>{@link #addParticipants(String, Set)} -> adiciona participantes ao grupo.</li>
 *     <li>{@link #transferManager(String, UUID)} -> passa a gestão para outro participante.</li>
 *     <li>{@link #removeParticipant(String, UUID, UUID)} -> remove um participante ou faz o user sair.</li>
 * </ul>
 */
@Service
public class GroupManagementService {

    private final ConversationAdmissionService admissions;
    private final RbacAuthorizationService rbac;
    private final UserAccessService userAccessService;
    private final SessionRevoker sessionRevoker;
    private final ConversationAuthorizationService resourceAccess;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;
    private final GroupMutationSupport groups;

    public GroupManagementService(ConversationAdmissionService admissions, RbacAuthorizationService rbac,
            UserAccessService users, SessionRevoker sessionRevoker, ConversationAuthorizationService authorization,
            ConversationChangeNotifier events, ConversationMutationGuard guard, GroupMutationSupport groups) {
        this.admissions = admissions;
        this.rbac = rbac;
        this.userAccessService = users;
        this.sessionRevoker = sessionRevoker;
        this.resourceAccess = authorization;
        this.stateEvents = events;
        this.guard = guard;
        this.groups = groups;
    }

    /** Adiciona participantes; elegibilidade é checada antes, e participação + estados são gravados juntos */
    public ConversationResponseDTO addParticipants(String id, Set<UUID> userIds) {
        boolean[] changed = { false };

        ConversationResponseDTO response = guard.execute(() -> {
            // só o gestor ativo de um grupo ativo adiciona
            UUID actor = rbac.currentUserId();
            userAccessService.requireActiveActor(actor);
            Conversation initial = groups.managedConversation(id);
            requireManageable(initial, actor);

            // convidados sem nulos, membros da comunidade (se houver), e os novos precisam estar ativos
            Set<UUID> requested = new LinkedHashSet<>(userIds);
            if (requested.contains(null)) throw new InvalidFieldException("Participantes não podem ser nulos.");
            validateCommunityMembers(initial, requested);
            Set<UUID> additions = new LinkedHashSet<>(requested);
            additions.removeAll(initial.getParticipantIds());
            userAccessService.requireActiveUsers(additions);

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

        if (changed[0]) stateEvents.publishConversationChanged(id);
        return response;
    }

    /** Passa a gestão para o sucessor; o antigo gestor perde a autoridade na hora */
    public ConversationResponseDTO transferManager(String id, UUID successorUserId) {
        ConversationResponseDTO response = guard.execute(() -> {
            // ator ativo, gestor do grupo, e sucessor válido e ativo
            UUID actor = rbac.currentUserId();
            userAccessService.requireActiveActor(actor);
            Conversation initial = groups.managedConversation(id);
            requireManageable(initial, actor);
            groups.validateSuccessor(initial, actor, successorUserId);
            requireSuccessorActive(successorUserId);

            // grava com CAS, revalidando na versão mais recente
            return toResponse(groups.mutateGroup(initial, latest -> {
                requireManageable(latest, actor);
                groups.validateSuccessor(latest, actor, successorUserId);
                return groups.writeManager(latest, successorUserId);
            }));
        });

        stateEvents.publishConversationChanged(id);
        return response;
    }

    /** Remove um participante; sair por conta própria funciona até com o grupo arquivado */
    public void removeParticipant(String id, UUID userId, UUID successorUserId) {
        boolean[] changed = { false };
        try {
            guard.execute(() -> {
                UUID actor = rbac.currentUserId();
                userAccessService.requireActiveActor(actor);
                Conversation initial = groups.managedConversation(id);
                authorizeRemoval(initial, actor, userId);
                if (!initial.hasParticipant(userId)) return null;

                // quem sai sozinho e já não está na comunidade não indica sucessor
                ProjectCommunity parent = groups.communityFor(initial);
                UUID successor = actor.equals(userId) && parent != null && !parent.isMember(actor)
                        ? null : successorUserId;
                groups.validateRemoval(initial, actor, userId, successor);
                if (successor != null) requireSuccessorActive(successor);

                groups.mutateGroup(initial, latest -> {
                    authorizeRemoval(latest, actor, userId);
                    if (!latest.hasParticipant(userId)) return null;
                    groups.validateRemoval(latest, actor, userId, successor);

                    // tira o user; se ele era o gestor, o sucessor assume; grupo vazio fica sem gestor
                    Set<UUID> survivors = new LinkedHashSet<>(latest.getParticipantIds());
                    survivors.remove(userId);
                    UUID manager = latest.getManagerUserId();
                    if (survivors.isEmpty()) manager = null;
                    else if (userId.equals(manager)) manager = successor;
                    return groups.writeParticipants(latest, survivors, manager);
                });

                // avisa quem saiu e derruba as sessões dele
                changed[0] = true;
                stateEvents.publishRemovedConversation(userId, id);
                sessionRevoker.revokeUser(userId);
                return null;
            });
        } finally {
            if (changed[0]) stateEvents.publishConversationChanged(id);
        }
    }

    /** Gestor atual de um grupo ativo, numa comunidade ativa (se houver) */
    private void requireManageable(Conversation conversation, UUID actor) {
        authorizeManagement(conversation, actor);
        resourceAccess.requireActive(conversation);
        resourceAccess.requireCommunityWriter(conversation, actor);
    }

    /** O ator precisa participar e ser o gestor do grupo */
    private void authorizeManagement(Conversation conversation, UUID actor) {
        resourceAccess.requireParticipant(conversation.getId(), actor);
        if (!actor.equals(conversation.getManagerUserId())) {
            throw new UnauthorizedAccessException("A gestão exige o gestor atual do grupo.");
        }
    }

    /** Sair por conta própria só exige participar; remover outra pessoa exige gestão */
    private void authorizeRemoval(Conversation conversation, UUID actor, UUID target) {
        if (!actor.equals(target)) {
            requireManageable(conversation, actor);
            return;
        }

        // gestor que já saiu da comunidade não pode sair do grupo sozinho (fica sem sucessor válido)
        if (!conversation.hasParticipant(actor)) return;
        ProjectCommunity community = groups.communityFor(conversation);
        if (community != null && !community.isMember(actor) && actor.equals(conversation.getManagerUserId())) {
            throw new GroupManagerUnavailableException();
        }
    }

    /** Sucessor inexistente ou inativo na Auth vira sucessor inelegível */
    private void requireSuccessorActive(UUID successor) {
        try {
            userAccessService.requireActiveUsers(Set.of(successor));
        } catch (ResourceNotFoundException | UnauthorizedAccessException unavailable) {
            throw new SuccessorIneligibleException();
        }
    }

    /** Em subgrupo, todos os convidados precisam ser membros da comunidade */
    private void validateCommunityMembers(Conversation conversation, Set<UUID> users) {
        ProjectCommunity community = groups.communityFor(conversation);
        if (community != null && users.stream().anyMatch(user -> !community.isMember(user))) {
            throw new BusinessRuleException("Todos os participantes precisam ser membros da comunidade.");
        }
    }
}
