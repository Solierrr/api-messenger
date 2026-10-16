package com.solaria.messenger.core.service.community;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MembershipCleanupRequiredException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.SuccessorIneligibleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.core.service.conversation.GroupMutationSupport;

import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireRemovalAuthority;

/**
 * Service que propaga a saída de um membro da comunidade para os subgrupos dela
 *
 * <p>
 * Quando alguém sai da comunidade, ele também precisa sair dos grupos filhos. Se ele for gestor de algum
 * grupo, a gestão passa para o sucessor indicado antes da remoção.
 * </p>
 * <ul>
 *     <li>{@link #validateCommunityRemoval(ProjectCommunity, UUID, UUID, Map)} -> valida o plano de remoção e os sucessores.</li>
 *     <li>{@link #prepareCommunityRemoval(ProjectCommunity, UUID, UUID, Map, List)} -> transfere a gestão dos grupos antes da saída.</li>
 *     <li>{@link #cleanupCommunityRemoval(String, UUID, UUID)} -> tira o user dos subgrupos depois da saída.</li>
 *     <li>{@link #requireNoResidualCommunityMembership(String, Set)} -> barra readmissão enquanto houver resíduo nos subgrupos.</li>
 * </ul>
 */
@Service
public class CommunityGroupCascadeService {

    private final ConversationRepository conversationRepository;
    private final UserAccessService userAccessService;
    private final ConversationAuthorizationService resourceAccess;
    private final GroupMutationSupport groups;

    public CommunityGroupCascadeService(ConversationRepository conversations, UserAccessService users,
            ConversationAuthorizationService authorization, GroupMutationSupport groups) {
        this.conversationRepository = conversations;
        this.userAccessService = users;
        this.resourceAccess = authorization;
        this.groups = groups;
    }

    /** Valida o plano inteiro (sucessores e elegibilidade) antes de qualquer transferência ou remoção */
    public List<Conversation> validateCommunityRemoval(ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors) {
        List<Conversation> subgroups = findSubgroups(community.getId());

        // user já saiu da comunidade: sucessor não vale mais, só barra se ainda for gestor de grupo com outras pessoas
        if (!community.isMember(target)) {
            for (Conversation group : subgroups) {
                if (group.hasParticipant(target) && target.equals(group.getManagerUserId())
                        && group.getParticipantIds().size() > 1) {
                    throw new GroupManagerUnavailableException();
                }
            }
            return subgroups;
        }

        // cada sucessor indicado precisa apontar para um subgrupo envolvido na remoção
        Map<String, Conversation> byId = new LinkedHashMap<>();
        subgroups.forEach(group -> byId.put(group.getId(), group));
        for (var entry : successors.entrySet()) {
            Conversation group = byId.get(entry.getKey());
            if (group == null || (!group.hasParticipant(target) && !entry.getValue().equals(group.getManagerUserId()))) {
                throw new InvalidFieldException("Sucessor indicado para conversa alheia à remoção.");
            }
        }

        // valida a remoção em cada grupo e junta os sucessores que vão receber a gestão
        Set<UUID> transfers = new LinkedHashSet<>();
        for (Conversation group : subgroups) {
            if (!group.hasParticipant(target)) continue;
            groups.validateRemoval(group, actor, target, successors.get(group.getId()));
            if (target.equals(group.getManagerUserId()) && successors.get(group.getId()) != null) {
                transfers.add(successors.get(group.getId()));
            }
        }

        // os sucessores precisam ser users ativos
        try {
            userAccessService.requireActiveUsers(transfers);
        } catch (ResourceNotFoundException | UnauthorizedAccessException ineligible) {
            throw new SuccessorIneligibleException();
        }
        return subgroups;
    }

    /** Passa a gestão dos grupos que o user administra antes de ele sair da comunidade */
    public void prepareCommunityRemoval(ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors, List<Conversation> subgroups) {
        for (Conversation group : subgroups) {
            // só interessa grupo onde o user é gestor; se já saiu da comunidade, resta apenas a limpeza
            if (!group.hasParticipant(target) || !target.equals(group.getManagerUserId())) continue;
            if (!community.isMember(target)) continue;

            groups.mutateGroup(group, latest -> {
                // revalida tudo na versão mais recente do grupo e da comunidade
                if (!community.getId().equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
                ProjectCommunity parent = resourceAccess.requireCommunity(community.getId());
                requireRemovalAuthority(parent, actor, target);
                if (!parent.isMember(target)) throw new ConcurrentUpdateException();
                if (!latest.hasParticipant(target) || !target.equals(latest.getManagerUserId())) return null;

                // grupo só com o user, removido por outra pessoa: o grupo fica vazio
                if (latest.getParticipantIds().size() == 1 && !actor.equals(target)) {
                    return groups.writeParticipants(latest, Set.of(), null);
                }

                // senão, a gestão vai para o sucessor indicado
                UUID successor = successors.get(latest.getId());
                groups.validateSuccessor(latest, target, successor);
                return groups.writeManager(latest, successor);
            });
        }
    }

    /** Tira o user dos subgrupos; se a limpeza falhar, a saída da comunidade já bloqueia o acesso */
    public void cleanupCommunityRemoval(String communityId, UUID actor, UUID target) {
        for (Conversation group : findSubgroups(communityId)) {
            if (!group.hasParticipant(target)) continue;

            groups.mutateGroup(group, latest -> {
                // revalida na versão mais recente; o user já não pode estar na comunidade
                if (!communityId.equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
                ProjectCommunity parent = resourceAccess.requireCommunity(communityId);
                requireRemovalAuthority(parent, actor, target);
                if (parent.isMember(target)) throw new ConcurrentUpdateException();
                if (!latest.hasParticipant(target)) return null;

                // remove o user; grupo que ficou vazio perde o gestor
                Set<UUID> survivors = new LinkedHashSet<>(latest.getParticipantIds());
                survivors.remove(target);
                return groups.writeParticipants(latest, survivors, survivors.isEmpty() ? null : latest.getManagerUserId());
            });
        }
    }

    /** Barra a readmissão na comunidade enquanto o user ainda aparecer em algum subgrupo */
    public void requireNoResidualCommunityMembership(String communityId, Set<UUID> users) {
        for (UUID user : users) {
            if (conversationRepository.existsByCommunityIdAndParticipantIdsContaining(communityId, user)) {
                throw new MembershipCleanupRequiredException();
            }
        }
    }

    /** Subgrupos da comunidade, do mais recente para o mais antigo */
    private List<Conversation> findSubgroups(String communityId) {
        return conversationRepository.findByCommunityIdAndConversationTypeOrderByLastInteractionAtDesc(
                communityId, ConversationType.GROUP);
    }
}
