package com.solaria.messenger.core.helper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MembershipCleanupRequiredException;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;

import static com.solaria.messenger.core.helper.PureRulesHelper.applyOptimistic;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireRemovalAuthority;
import static com.solaria.messenger.core.helper.PureRulesHelper.withoutUser;

@Component
public class CommunityHelper {

    private final ConversationRepository conversationRepository;
    private final UserAccessHelper users;
    private final LookupHelper lookup;
    private final GroupHelper groups;

    public CommunityHelper(ConversationRepository conversations, UserAccessHelper users, LookupHelper lookup,
            GroupHelper groups) {
        this.conversationRepository = conversations;
        this.users = users;
        this.lookup = lookup;
        this.groups = groups;
    }

    // escrita da comunidade

    /** Aplica a escrita com CAS; se a versão mudou, relê a comunidade e tenta de novo */
    public ProjectCommunity mutateCommunity(ProjectCommunity initial, Function<ProjectCommunity, Long> mutation) {
        return applyOptimistic(initial, mutation, () -> lookup.requireCommunity(initial.getId()),
                this::concurrentModification);
    }

    public BusinessException concurrentModification() {
        return new ConcurrentUpdateException("A comunidade mudou durante a operação.");
    }

    /** Subgrupos da comunidade no formato de listagem */
    public List<SubgroupMetadataResponseDTO> subgroupMetadata(String communityId) {
        return findSubgroups(communityId).stream().map(ConversationMapper::toSubgroupMetadata).toList();
    }

    // cascata para os subgrupos

    /** Valida o plano inteiro (sucessores e elegibilidade) antes de qualquer transferência ou remoção */
    public List<Conversation> validateCommunityRemoval(ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors) {
        List<Conversation> subgroups = findSubgroups(community.getId());

        // user já saiu da comunidade: sucessor não vale mais, só barra se ainda for gestor de grupo com outras pessoas
        if (!community.isMember(target)) {
            if (subgroups.stream().anyMatch(group -> isManagerWithOthers(group, target))) {
                throw new GroupManagerUnavailableException();
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
            if (group.hasParticipant(target)) {
                UUID successor = successors.get(group.getId());
                groups.validateRemoval(group, actor, target, successor);
                if (successor != null && target.equals(group.getManagerUserId())) transfers.add(successor);
            }
        }

        // os sucessores precisam ser users ativos
        users.requireSuccessorsActive(transfers);
        return subgroups;
    }

    private static boolean isManagerWithOthers(Conversation group, UUID target) {
        return group.hasParticipant(target) && target.equals(group.getManagerUserId())
                && group.getParticipantIds().size() > 1;
    }

    /** Passa a gestão dos grupos que o user administra antes de ele sair da comunidade */
    public void prepareCommunityRemoval(ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors, List<Conversation> subgroups) {
        // só interessa grupo onde o user é gestor; se já saiu da comunidade, resta apenas a limpeza
        if (!community.isMember(target)) return;

        for (Conversation group : subgroups) {
            if (group.hasParticipant(target) && target.equals(group.getManagerUserId())) {
                groups.mutateGroup(group, latest -> transferManagement(latest, community, actor, target, successors));
            }
        }
    }

    /** Revalida na versão mais recente do grupo e da comunidade e passa a gestão; {@code null} = nada a gravar */
    private Long transferManagement(Conversation latest, ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors) {
        if (!community.getId().equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
        ProjectCommunity parent = lookup.requireParentCommunity(community.getId());
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
    }

    /** Tira o user dos subgrupos; se a limpeza falhar, a saída da comunidade já bloqueia o acesso */
    public void cleanupCommunityRemoval(String communityId, UUID actor, UUID target) {
        for (Conversation group : findSubgroups(communityId)) {
            if (!group.hasParticipant(target)) continue;

            groups.mutateGroup(group, latest -> {
                // revalida na versão mais recente; o user já não pode estar na comunidade
                if (!communityId.equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
                ProjectCommunity parent = lookup.requireParentCommunity(communityId);
                requireRemovalAuthority(parent, actor, target);
                if (parent.isMember(target)) throw new ConcurrentUpdateException();
                if (!latest.hasParticipant(target)) return null;

                // remove o user; grupo que ficou vazio perde o gestor
                Set<UUID> survivors = withoutUser(latest.getParticipantIds(), target);
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
