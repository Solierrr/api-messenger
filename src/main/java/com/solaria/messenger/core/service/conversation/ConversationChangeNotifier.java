package com.solaria.messenger.core.service.conversation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.port.ConversationEventPublisher;

/**
 * Service que avisa os participantes quando uma conversa muda
 *
 * <p>
 * Para cada participante autorizado, monta o mesmo item da listagem REST e entrega pela porta de eventos.
 * Roda depois do commit e fora do guard; falhas só geram log.
 * </p>
 * <ul>
 *     <li>{@link #publishConversationChanged(String)} -> avisa todos os participantes de uma conversa.</li>
 *     <li>{@link #publishConversationChanged(String, Set)} -> avisa só os users informados.</li>
 *     <li>{@link #publishConversationsChanged(Set)} -> avisa várias conversas de uma vez.</li>
 *     <li>{@link #publishRemovedConversation(UUID, String)} -> avisa o user que ele perdeu a conversa.</li>
 * </ul>
 */
@Service
public class ConversationChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(ConversationChangeNotifier.class);
    private final ConversationRepository conversationRepository;
    private final ConversationAuthorizationService resources;
    private final ConversationProjectionService projections;
    private final ConversationSyncClock clock;
    private final ConversationMutationGuard guard;
    private final ConversationEventPublisher events;

    public ConversationChangeNotifier(ConversationRepository conversations, ConversationAuthorizationService resources,
            ConversationProjectionService projections, ConversationSyncClock clock, ConversationMutationGuard guard,
            ConversationEventPublisher events) {
        this.conversationRepository = conversations;
        this.resources = resources;
        this.projections = projections;
        this.clock = clock;
        this.guard = guard;
        this.events = events;
    }

    /** Avisa o estado atual da conversa a todos os participantes, mesmo os que não estão com ela aberta */
    public void publishConversationChanged(String id) {
        publishConversationsChanged(Set.of(id), null);
    }

    /** Avisa só os users informados, como no PATCH do estado pessoal */
    public void publishConversationChanged(String id, Set<UUID> only) {
        publishConversationsChanged(Set.of(id), only);
    }

    /** Avisa várias conversas num lote só (usado pela cascata da comunidade) */
    public void publishConversationsChanged(Set<String> ids) {
        publishConversationsChanged(ids, null);
    }

    /** Aviso mínimo, sem dados da conversa, para quem acabou de perder o acesso */
    public void publishRemovedConversation(UUID recipient, String conversationId) {
        events.conversationRemoved(recipient, conversationId, clock.epoch(), clock.nextVersion());
    }

    /** Monta os itens dentro do guard e entrega fora dele */
    private void publishConversationsChanged(Set<String> ids, Set<UUID> only) {
        // publicar de dentro do guard travaria a fronteira local
        if (guard.isHeldByCurrentThread()) throw new IllegalStateException("Publicação deve ocorrer fora da fronteira.");
        if (ids.isEmpty()) return;

        try {
            Map<UUID, List<ConversationListItemResponseDTO>> projected = guard.execute(() -> {
                // carrega as conversas, as comunidades e quem pode ver cada uma
                List<Conversation> current = conversationRepository.findAllById(ids);
                Map<String, ProjectCommunity> parents = resources.communitiesFor(current);
                Map<String, Set<UUID>> authorized = resources.authorizedParticipants(current, parents);

                // se veio uma lista de destinatários, mantém só eles
                if (only != null) authorized.replaceAll((id, users) -> {
                    Set<UUID> selected = new LinkedHashSet<>(users);
                    selected.retainAll(only);
                    return selected;
                });

                return projections.project(current, authorized, parents, null);
            });

            deliver(projected);

        } catch (RuntimeException publicationFailure) {
            log.warn("Falha ao publicar projeções das conversas {}", ids, publicationFailure);
        }
    }

    /** Confere a autorização de novo logo antes de entregar; quem perdeu acesso no meio do caminho não recebe */
    private void deliver(Map<UUID, List<ConversationListItemResponseDTO>> projected) {
        Set<String> ids = projected.values().stream().flatMap(List::stream)
                .map(ConversationListItemResponseDTO::id).collect(Collectors.toSet());
        Map<String, Set<UUID>> authorized = resources.authorizedParticipants(ids);

        projected.forEach((recipient, items) -> {
            for (ConversationListItemResponseDTO item : items) {
                if (authorized.getOrDefault(item.id(), Set.of()).contains(recipient)) {
                    events.conversationUpdated(recipient, item);
                }
            }
        });
    }
}
