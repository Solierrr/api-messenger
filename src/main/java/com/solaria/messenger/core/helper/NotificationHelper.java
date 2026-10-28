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

/**
 * Avisa os clientes sobre mudanças em conversas e mensagens.
 *
 * <p>
 * Monta a projeção da lista de conversas dentro da fronteira do guard e entrega fora dela. Antes de entregar,
 * confere a autorização de novo, para que quem perdeu acesso no caminho não receba. Falha na publicação só gera log,
 * porque a gravação já aconteceu.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> carrega as conversas afetadas.</li>
 *     <li>{@link ConversationAccessHelper} -> carrega as comunidades e confere quem pode ver cada conversa.</li>
 *     <li>{@link ConversationProjectionHelper} -> monta os itens da lista.</li>
 *     <li>{@link ConversationSyncClock} -> fornece a versão do evento.</li>
 *     <li>{@link ConversationMutationGuard} -> garante que a entrega sai fora da fronteira local.</li>
 *     <li>{@link ConversationEventPublisher} -> envia eventos de conversa.</li>
 *     <li>{@link MessageEventPublisher} -> envia eventos de mensagem.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #publishConversationChanged(String)} -> avisa a mudança a todos os participantes.</li>
 *     <li>{@link #publishConversationChanged(String, Set)} -> avisa só os usuários informados.</li>
 *     <li>{@link #publishConversationsChanged(Set)} -> avisa várias conversas num lote.</li>
 *     <li>{@link #publishRemovedConversation(UUID, String)} -> avisa que o user perdeu a conversa.</li>
 *     <li>{@link #notifyMessageCreated(Message)} -> avisa a criação de mensagem.</li>
 *     <li>{@link #notifyMessageEdited(Message)} -> avisa a edição de mensagem.</li>
 *     <li>{@link #notifyMessageDeleted(Message)} -> avisa a exclusão de mensagem.</li>
 * </ul>
 */
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

    /**
     * Avisa o estado atual da conversa a todos os participantes.
     *
     * <p>
     * Mesmo quem não está com a conversa aberta recebe o aviso.
     * </p>
     *
     * @param id identificador da conversa.
     */
    public void publishConversationChanged(String id) {
        publishConversationsChanged(Set.of(id), null);
    }

    /**
     * Avisa a mudança da conversa só aos usuários informados.
     *
     * <p>
     * Usado no PATCH do estado pessoal, em que só o próprio user precisa saber.
     * </p>
     *
     * @param id identificador da conversa.
     * @param only usuários que devem receber o aviso.
     */
    public void publishConversationChanged(String id, Set<UUID> only) {
        publishConversationsChanged(Set.of(id), only);
    }

    /**
     * Avisa a mudança de várias conversas num lote só.
     *
     * <p>
     * Usado pela cascata de comunidade, que mexe em vários subgrupos de uma vez.
     * </p>
     *
     * @param ids identificadores das conversas.
     */
    public void publishConversationsChanged(Set<String> ids) {
        publishConversationsChanged(ids, null);
    }

    /**
     * Avisa que o user perdeu acesso à conversa.
     *
     * <p>
     * O aviso é mínimo: traz só o identificador, sem dados da conversa.
     * </p>
     *
     * @param recipient user que perdeu o acesso.
     * @param conversationId identificador da conversa.
     */
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

    /**
     * Avisa a criação de uma mensagem e atualiza a prévia e as não lidas da conversa.
     *
     * @param message mensagem gravada.
     */
    public void notifyMessageCreated(Message message) {
        messageEvents.messageCreated(message);
        publishConversationState(message.getConversationId());
    }

    /**
     * Avisa a edição de uma mensagem e atualiza a prévia da conversa.
     *
     * @param message mensagem editada.
     */
    public void notifyMessageEdited(Message message) {
        messageEvents.messageEdited(message);
        publishConversationState(message.getConversationId());
    }

    /**
     * Avisa a exclusão de uma mensagem e atualiza a prévia e as não lidas da conversa.
     *
     * @param message mensagem excluída.
     */
    public void notifyMessageDeleted(Message message) {
        messageEvents.messageDeleted(message);
        publishConversationState(message.getConversationId());
    }

    /** A mensagem já foi persistida; uma falha ao invalidar a lista nunca vira erro do comando */
    private void publishConversationState(String conversationId) {
        try {
            publishConversationChanged(conversationId);
        } catch (RuntimeException publicationFailure) {
            log.warn("Falha ao invalidar lista da conversa {} após alteração persistida", conversationId,
                    publicationFailure);
        }
    }
}
