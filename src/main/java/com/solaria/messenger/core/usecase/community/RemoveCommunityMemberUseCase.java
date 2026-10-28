package com.solaria.messenger.core.usecase.community;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityMemberRemovalRequestDTO;
import com.solaria.messenger.core.contract.request.SubgroupSuccessorRequestDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.RemovalIncompleteException;
import com.solaria.messenger.core.helper.CommunityHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.port.SessionRevoker;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.copyMembers;
import static com.solaria.messenger.core.helper.PureRulesHelper.nextVersion;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireRemovalAuthority;

@Service
public class RemoveCommunityMemberUseCase {

    private static final Logger log = LoggerFactory.getLogger(RemoveCommunityMemberUseCase.class);
    private final CommunityRepository communityRepository;
    private final CommunityHelper communities;
    private final LookupHelper lookup;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;
    private final RbacAuthorizationService rbac;
    private final SessionRevoker sessionRevoker;
    private final UserAccessHelper users;

    public RemoveCommunityMemberUseCase(CommunityRepository communityRepository, CommunityHelper communities,
            LookupHelper lookup, NotificationHelper notifications, ConversationMutationGuard guard,
            RbacAuthorizationService rbac, SessionRevoker sessionRevoker, UserAccessHelper users) {
        this.communityRepository = communityRepository;
        this.communities = communities;
        this.lookup = lookup;
        this.notifications = notifications;
        this.guard = guard;
        this.rbac = rbac;
        this.sessionRevoker = sessionRevoker;
        this.users = users;
    }

    /**
     * Remove o membro da comunidade e dos subgrupos, retomando do estado atual se for repetido.
     * Não devolve a lista de membros, porque quem chama pode ter acabado de sair.
     */
    public void execute(String id, UUID userId, CommunityMemberRemovalRequestDTO dto) {
        Map<String, UUID> successors = successorMap(dto);
        Set<String> affectedConversations = new LinkedHashSet<>();
        try {
            guard.execute(() -> {
                // ator ativo, sucessores completos e permissão para remover
                UUID actor = rbac.currentUserId();
                users.requireActiveActor(actor);
                ProjectCommunity initial = lookup.requireCommunity(id);
                Map<String, UUID> requested = new LinkedHashMap<>(successors);
                if (requested.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null)) {
                    throw new InvalidFieldException("Sucessores precisam informar conversa e usuário.");
                }
                requireRemovalAuthority(initial, actor, userId);

                // valida o plano nos subgrupos e anota as conversas que serão afetadas
                List<Conversation> groups = communities.validateCommunityRemoval(initial, actor, userId, requested);
                groups.forEach(group -> affectedConversations.add(group.getId()));
                boolean affected = initial.isMember(userId) || groups.stream().anyMatch(group -> group.hasParticipant(userId));

                try {
                    // 1. passa a gestão dos subgrupos para os sucessores
                    communities.prepareCommunityRemoval(initial, actor, userId, requested, groups);

                    // 2. tira o user da comunidade
                    ProjectCommunity after = communities.mutateCommunity(initial, latest -> {
                        requireRemovalAuthority(latest, actor, userId);
                        if (!latest.isMember(userId)) return null;
                        List<CommunityMember> survivors = copyMembers(latest.getMembers());
                        survivors.removeIf(member -> member.getUserId().equals(userId));
                        return communityRepository.updateMembersIfCurrent(latest.getId(), latest.getVersion(), survivors,
                                Instant.now(), nextVersion(latest.getVersion()));
                    });

                    // 3. avisa o user que ele perdeu os subgrupos
                    if (!after.isMember(userId)) {
                        groups.stream().filter(group -> group.hasParticipant(userId))
                                .forEach(group -> notifications.publishRemovedConversation(userId, group.getId()));
                    }

                    // 4. tira o user dos subgrupos
                    communities.cleanupCommunityRemoval(id, actor, userId);

                } catch (BusinessException business) {
                    throw business;
                } catch (RuntimeException failure) {
                    // falha técnica no meio da cascata: o DELETE pode ser repetido e continua de onde parou
                    log.error("Falha na remoção de membro; o DELETE pode ser repetido (communityId={}, userId={})",
                            id, userId, failure);
                    BusinessException incomplete = new RemovalIncompleteException();
                    incomplete.addSuppressed(failure);
                    throw incomplete;
                } finally {
                    // derruba as sessões do user para ele perder as inscrições já abertas
                    if (affected) sessionRevoker.revokeUser(userId);
                }
                return null;
            });
        } finally {
            notifications.publishConversationsChanged(affectedConversations);
        }
    }

    /** Converte o corpo opcional em mapa conversa -> sucessor, recusando entradas incompletas ou repetidas */
    private static Map<String, UUID> successorMap(CommunityMemberRemovalRequestDTO dto) {
        if (dto == null || dto.successors() == null || dto.successors().isEmpty()) {
            return Map.of();
        }

        Map<String, UUID> result = new LinkedHashMap<>();
        for (SubgroupSuccessorRequestDTO successor : dto.successors()) {
            if (successor == null || successor.conversationId() == null || successor.successorUserId() == null) {
                throw new InvalidFieldException("Cada sucessor precisa informar conversa e usuário");
            }

            // id do sucessor em formato inválido vira erro de campo
            UUID successorUserId;
            try {
                successorUserId = successor.successorUserIdValue();
            } catch (IllegalArgumentException exception) {
                throw new InvalidFieldException(exception.getMessage());
            }

            if (result.containsKey(successor.conversationId())) {
                throw new InvalidFieldException("A conversa não pode aparecer mais de uma vez nos sucessores");
            }
            result.put(successor.conversationId(), successorUserId);
        }
        return Collections.unmodifiableMap(result);
    }
}
