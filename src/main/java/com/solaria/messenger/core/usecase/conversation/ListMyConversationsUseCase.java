package com.solaria.messenger.core.usecase.conversation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.contract.response.ConversationListSnapshotDTO;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.ConversationProjectionHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

/**
 * Caso de uso que lista as conversas do user atual.
 *
 * <p>
 * Devolve um snapshot com a época e a versão da sincronização. O app usa a versão para reconciliar a lista,
 * inclusive quando a lista vem vazia. Os itens saem da interação mais recente para a mais antiga. Cada item traz só os
 * dados visíveis para o próprio user.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> busca as conversas do user.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link ConversationAccessHelper} -> carrega as comunidades e quem está autorizado.</li>
 *     <li>{@link ConversationProjectionHelper} -> monta os itens da lista.</li>
 *     <li>{@link ConversationSyncClock} -> fornece a época e a versão.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a leitura na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(boolean)} -> lista as conversas com época e versão.</li>
 * </ul>
 */
@Service
public class ListMyConversationsUseCase {

    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final ConversationProjectionHelper projections;
    private final ConversationSyncClock clock;
    private final ConversationMutationGuard guard;

    public ListMyConversationsUseCase(ConversationRepository conversations, RbacAuthorizationService rbac,
            ConversationAccessHelper access, ConversationProjectionHelper projections,
            ConversationSyncClock clock, ConversationMutationGuard guard) {
        this.conversationRepository = conversations;
        this.rbac = rbac;
        this.access = access;
        this.projections = projections;
        this.clock = clock;
        this.guard = guard;
    }

    /**
     * Lista as conversas do user com época e versão.
     *
     * @param archived repassado à projeção, para filtrar pelo estado de arquivamento.
     * @return snapshot com época, versão e itens ordenados pela última interação, do mais recente para o mais antigo.
     */
    public ConversationListSnapshotDTO execute(boolean archived) {
        long version = clock.nextVersion();
        return guard.execute(() -> {
            // conversas do user e quem está autorizado em cada uma
            UUID actor = rbac.currentUserId();
            List<Conversation> candidates = conversationRepository
                    .findByParticipantIdsContainingOrderByLastInteractionAtDesc(actor);
            Map<String, ProjectCommunity> parents = access.communitiesFor(candidates);
            Map<String, Set<UUID>> authorized = access.authorizedParticipants(candidates, parents);

            // mantém só o próprio user como destinatário
            authorized.replaceAll((id, users) -> users.contains(actor) ? Set.of(actor) : Set.of());

            // monta os itens e ordena da interação mais recente para a mais antiga
            List<ConversationListItemResponseDTO> items = projections.project(candidates, authorized, parents, archived, version)
                    .getOrDefault(actor, List.of()).stream()
                    .sorted(Comparator.comparing(ConversationListItemResponseDTO::lastInteractionAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(ConversationListItemResponseDTO::id)).toList();

            return new ConversationListSnapshotDTO(clock.epoch(), version, items);
        });
    }
}
