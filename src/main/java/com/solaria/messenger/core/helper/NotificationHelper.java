package com.solaria.messenger.core.helper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.port.ConversationEventPublisher;
import com.solaria.messenger.core.port.MessageEventPublisher;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

@Component
public class NotificationHelper {

    private static final Logger log = LoggerFactory.getLogger(NotificationHelper.class);
    private final ConversationRepository conversationRepository;
    private final ConversationAccessHelper resources;
    private final ConversationProjectionHelper projections;
    private final ConversationSyncClock clock;
    private final ConversationMutationGuard guard;
    private final ConversationEventPublisher events;
    private final MessageEventPublisher messageEvents;

    public NotificationHelper(ConversationRepository conversations, ConversationAccessHelper resources,
            ConversationProjectionHelper projections, ConversationSyncClock clock, ConversationMutationGuard guard,
            ConversationEventPublisher events, MessageEventPublisher messageEvents) {
        this.conversationRepository = conversations;
        this.resources = resources;
        this.projections = projections;
        this.clock = clock;
        this.guard = guard;
        this.events = events;
        this.messageEvents = messageEvents;
    }

    // conversa

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
            // versão antes de qualquer leitura: é ela que ordena os eventos entre pods
            long version = clock.nextVersion();
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

                return projections.project(current, authorized, parents, null, version);
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

    // mensagem

    /** Anuncia uma mensagem nova e atualiza preview e não lidas da conversa. */
    public void notifyMessageCreated(Message message) {
        messageEvents.messageCreated(message);
        publishConversationState(message.getConversationId());
    }

    /** Anuncia a edição efetiva e atualiza o preview da conversa. */
    public void notifyMessageEdited(Message message) {
        messageEvents.messageEdited(message);
        publishConversationState(message.getConversationId());
    }

    /** Anuncia o tombstone e atualiza preview e não lidas da conversa. */
    public void notifyMessageDeleted(Message message) {
        messageEvents.messageDeleted(message);
        publishConversationState(message.getConversationId());
    }

    /** A mensagem já foi persistida; uma falha ao invalidar a lista nunca vira erro do comando. */
    private void publishConversationState(String conversationId) {
        try {
            publishConversationChanged(conversationId);
        } catch (RuntimeException publicationFailure) {
            log.warn("Falha ao invalidar lista da conversa {} após alteração persistida", conversationId,
                    publicationFailure);
        }
    }
}
