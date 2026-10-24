package com.solaria.messenger.core.service.conversation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.contract.response.ConversationListSnapshotDTO;
import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;

/**
 * Service de leitura de conversas
 *
 * <p>
 * Só consulta: não grava nada nem publica eventos. Toda leitura confere se o user ainda participa.
 * </p>
 * <ul>
 *     <li>{@link #findById(String)} -> busca uma conversa que o user participa.</li>
 *     <li>{@link #findMineSnapshot(boolean)} -> lista as conversas do user, arquivadas ou não.</li>
 *     <li>{@link #findSubgroupMetadataByCommunity(String)} -> lista todos os subgrupos da comunidade.</li>
 *     <li>{@link #findSubgroupMetadataByCommunityAndParticipant(String, UUID)} -> lista os subgrupos que o user participa.</li>
 * </ul>
 */
@Service
public class ConversationQueryService {

    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;
    private final ConversationAuthorizationService resourceAccess;
    private final ConversationProjectionService projections;
    private final ConversationSyncClock clock;
    private final ConversationMutationGuard guard;

    public ConversationQueryService(ConversationRepository conversations, RbacAuthorizationService rbac,
            ConversationAuthorizationService authorization, ConversationProjectionService projections,
            ConversationSyncClock clock, ConversationMutationGuard guard) {
        this.conversationRepository = conversations;
        this.rbac = rbac;
        this.resourceAccess = authorization;
        this.projections = projections;
        this.clock = clock;
        this.guard = guard;
    }

    /** Busca a conversa depois de confirmar que o user ainda participa */
    public ConversationResponseDTO findById(String id) {
        return resourceAccess.withAccess(id, rbac.currentUserId(), ConversationMapper::toResponse);
    }

    /** Lista as conversas do user com época e versão, para o app reconciliar mesmo quando a lista vem vazia */
    public ConversationListSnapshotDTO findMineSnapshot(boolean archived) {
        return guard.execute(() -> {
            // conversas do user e quem está autorizado em cada uma
            UUID actor = rbac.currentUserId();
            List<Conversation> candidates = conversationRepository
                    .findByParticipantIdsContainingOrderByLastInteractionAtDesc(actor);
            Map<String, ProjectCommunity> parents = resourceAccess.communitiesFor(candidates);
            Map<String, Set<UUID>> authorized = resourceAccess.authorizedParticipants(candidates, parents);

            // mantém só o próprio user como destinatário
            authorized.replaceAll((id, users) -> users.contains(actor) ? Set.of(actor) : Set.of());

            // monta os itens e ordena da interação mais recente para a mais antiga
            List<ConversationListItemResponseDTO> items = projections.project(candidates, authorized, parents, archived)
                    .getOrDefault(actor, List.of()).stream()
                    .sorted(Comparator.comparing(ConversationListItemResponseDTO::lastInteractionAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(ConversationListItemResponseDTO::id)).toList();

            // lista vazia ainda recebe uma versão nova, para não apagar um evento que chegue depois
            long version = items.isEmpty() ? clock.nextVersion() : items.getFirst().syncVersion();
            return new ConversationListSnapshotDTO(clock.epoch(), version, items);
        });
    }

    /** Todos os subgrupos da comunidade (visão de OWNER/ADMIN) */
    public List<SubgroupMetadataResponseDTO> findSubgroupMetadataByCommunity(String communityId) {
        return conversationRepository.findByCommunityIdAndConversationTypeOrderByLastInteractionAtDesc(
                communityId, ConversationType.GROUP).stream().map(ConversationMapper::toSubgroupMetadata).toList();
    }

    /** Só os subgrupos que o membro comum participa */
    public List<SubgroupMetadataResponseDTO> findSubgroupMetadataByCommunityAndParticipant(String communityId, UUID authId) {
        return conversationRepository.findByCommunityIdAndConversationTypeAndParticipantIdsContainingOrderByLastInteractionAtDesc(
                communityId, ConversationType.GROUP, authId).stream().map(ConversationMapper::toSubgroupMetadata).toList();
    }
}
