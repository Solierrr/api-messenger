package com.solaria.messenger.core.service.community;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.MembershipCleanupRequiredException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.OptimisticMutation;
import com.solaria.messenger.core.service.conversation.GroupMutationSupport;

/**
 * Service que propaga a saída de um membro da comunidade para os subgrupos dela
 *
 * <p>
 * Quem sai da comunidade também sai dos subgrupos. Subgrupo gerido por quem saiu é desfeito, porque não há
 * sucessor: todos os participantes saem e a conversa fica desativada. A conversa geral do projeto fica de fora,
 * porque acompanha os membros sozinha.
 * </p>
 * <ul>
 *     <li>{@link #cleanupCommunityRemoval(String, UUID)} -> tira o user dos subgrupos e desfaz os que ele geria.</li>
 *     <li>{@link #requireNoResidualCommunityMembership(String, Set)} -> barra readmissão enquanto houver resíduo nos subgrupos.</li>
 * </ul>
 */
@Service
public class CommunityGroupCascadeService {

    private final ConversationRepository conversationRepository;
    private final ConversationAuthorizationService resourceAccess;
    private final GroupMutationSupport groups;

    public CommunityGroupCascadeService(ConversationRepository conversations,
            ConversationAuthorizationService authorization, GroupMutationSupport groups) {
        this.conversationRepository = conversations;
        this.resourceAccess = authorization;
        this.groups = groups;
    }

    /**
     * Tira da comunidade já sem o user os subgrupos dele; devolve, por conversa alterada, quem perdeu o acesso.
     * Se a limpeza falhar no meio, a saída da comunidade já bloqueia o acesso e a readmissão refaz a limpeza.
     */
    public Map<String, Set<UUID>> cleanupCommunityRemoval(String communityId, UUID target) {
        Map<String, Set<UUID>> lost = new LinkedHashMap<>();
        for (Conversation group : findSubgroups(communityId)) {
            if (!group.hasParticipant(target)) continue;

            groups.mutateGroup(group, latest -> {
                if (!communityId.equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
                ProjectCommunity parent = resourceAccess.requireCommunity(communityId);
                if (parent.isMember(target)) throw new ConcurrentUpdateException();
                if (!latest.hasParticipant(target)) return null;

                long modified;
                Set<UUID> removed;
                if (target.equals(latest.getManagerUserId())) {
                    removed = Set.copyOf(latest.getParticipantIds());
                    modified = conversationRepository.dissolveIfCurrent(latest.getId(), latest.getVersion(),
                            Instant.now(), OptimisticMutation.nextVersion(latest.getVersion()));
                } else {
                    removed = Set.of(target);
                    Set<UUID> survivors = new LinkedHashSet<>(latest.getParticipantIds());
                    survivors.remove(target);
                    modified = groups.writeParticipants(latest, survivors,
                            survivors.isEmpty() ? null : latest.getManagerUserId());
                }
                if (modified == 1) lost.put(latest.getId(), removed);
                return modified;
            });
        }
        return lost;
    }

    /** Barra a readmissão na comunidade enquanto o user ainda aparecer em algum subgrupo */
    public void requireNoResidualCommunityMembership(String communityId, Set<UUID> users) {
        List<Conversation> subgroups = findSubgroups(communityId);
        for (UUID user : users) {
            if (subgroups.stream().anyMatch(group -> group.hasParticipant(user))) {
                throw new MembershipCleanupRequiredException();
            }
        }
    }

    /** Subgrupos da comunidade, sem a conversa geral, do mais recente para o mais antigo */
    private List<Conversation> findSubgroups(String communityId) {
        return conversationRepository.findByCommunityIdAndConversationTypeOrderByLastInteractionAtDesc(
                communityId, ConversationType.GROUP).stream().filter(group -> !group.isProjectGeneral()).toList();
    }
}
