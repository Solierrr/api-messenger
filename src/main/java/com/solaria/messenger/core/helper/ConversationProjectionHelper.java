package com.solaria.messenger.core.helper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.contract.response.Preview;
import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.repository.projection.ConversationMessageHead;
import com.solaria.messenger.core.repository.projection.LatestMessage;
import com.solaria.messenger.core.state.OwnedState;
import com.solaria.messenger.core.support.ConversationSyncClock;

/**
 * Monta a lista de conversas de cada destinatário, com prévia e não lidas.
 *
 * <p>
 * Trabalha em lote para evitar uma consulta por conversa. Cada item mostra o estado pessoal do destinatário, a
 * última mensagem como prévia e a contagem de não lidas. Cursor de leitura depois da última mensagem gravada é tratado
 * como erro. Conversa do tipo {@code CHAT_BOT} não tem estado pessoal, então os campos pessoais saem nulos.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationStateHelper} -> carrega o estado pessoal em lote.</li>
 *     <li>{@link MessageRepository} -> carrega a última mensagem e as não lidas.</li>
 *     <li>{@link ConversationSyncClock} -> fornece a época da sincronização.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #project(List, Map, Map, Boolean, long)} -> monta os itens por destinatário.</li>
 * </ul>
 */
@Component
public class ConversationProjectionHelper {

    private final ConversationStateHelper personalStates;
    private final MessageRepository messages;
    private final ConversationSyncClock clock;

    public ConversationProjectionHelper(ConversationStateHelper states, MessageRepository messages,
            ConversationSyncClock clock) {
        this.personalStates = states;
        this.messages = messages;
        this.clock = clock;
    }

    /**
     * Monta os itens por destinatário.
     *
     * <p>
     * {@code archivedFilter} nulo não filtra pelo arquivamento pessoal. Um valor booleano mantém só os destinatários
     * com o mesmo estado de arquivamento. Conversa de chatbot, sem estado pessoal, só passa quando o filtro pede os não
     * arquivados.
     * </p>
     *
     * @param conversations conversas a projetar.
     * @param authorized destinatários autorizados por conversa.
     * @param parents comunidades do lote, por id.
     * @param archivedFilter filtro de arquivamento, ou nulo para não filtrar.
     * @param version versão da sincronização, alocada pelo chamador antes da leitura das conversas.
     * @return mapa de id do destinatário para os itens da lista dele.
     * @throws BaselineUnavailableException caso o estado pessoal esteja ausente ou o cursor passe da última mensagem.
     */
    public Map<UUID, List<ConversationListItemResponseDTO>> project(List<Conversation> conversations,
            Map<String, Set<UUID>> authorized, Map<String, ProjectCommunity> parents, Boolean archivedFilter, long version) {
        // carrega de uma vez o estado pessoal de todos os users nas conversas humanas (chatbot não tem estado)
        Set<UUID> users = authorized.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Set<String> humanIds = conversations.stream().filter(c -> c.getConversationType() != ConversationType.CHAT_BOT)
                .filter(c -> !authorized.getOrDefault(c.getId(), Set.of()).isEmpty())
                .map(Conversation::getId).collect(Collectors.toSet());
        Map<String, Map<UUID, OwnedState>> states = personalStates.findOwnedBatch(users, humanIds);

        // escolhe os destinatários de cada conversa (aplicando o filtro de archive) e junta os cursores de leitura
        Map<String, Set<UUID>> selected = new LinkedHashMap<>();
        Map<String, Map<UUID, Integer>> cursors = new LinkedHashMap<>();
        for (Conversation conversation : conversations) {
            Set<UUID> recipients = new LinkedHashSet<>();
            for (UUID actor : authorized.getOrDefault(conversation.getId(), Set.of())) {
                OwnedState state = stateOf(states, conversation, actor);
                if (conversation.getConversationType() != ConversationType.CHAT_BOT && state == null) throw new BaselineUnavailableException();
                if (!passesArchiveFilter(state, archivedFilter)) continue;
                recipients.add(actor);
                if (state != null) cursors.computeIfAbsent(conversation.getId(), ignored -> new LinkedHashMap<>())
                        .put(actor, state.lastReadSequence());
            }
            if (!recipients.isEmpty()) selected.put(conversation.getId(), recipients);
        }

        // última mensagem e contagem de não lidas em consultas de lote
        Map<String, ConversationMessageHead> heads = messages.findLatestByConversationIds(selected.keySet());
        Map<String, Map<UUID, Long>> unread = messages.countUnreadByConversationAndUser(cursors);

        // monta o item de cada destinatário
        Map<UUID, List<ConversationListItemResponseDTO>> result = new LinkedHashMap<>();
        for (Conversation conversation : conversations) {
            for (UUID actor : selected.getOrDefault(conversation.getId(), Set.of())) {
                ConversationListItemResponseDTO item = listItem(conversation, stateOf(states, conversation, actor),
                        heads.get(conversation.getId()), unread.getOrDefault(conversation.getId(), Map.of()).get(actor), version,
                        conversation.getCommunityId() == null ? null : parents.get(conversation.getCommunityId()).getStatus());
                result.computeIfAbsent(actor, ignored -> new ArrayList<>()).add(item);
            }
        }
        return result;
    }

    /** Sem filtro tudo passa; sem estado (chatbot) só passa quando o filtro pede os não arquivados */
    private static boolean passesArchiveFilter(OwnedState state, Boolean archivedFilter) {
        if (archivedFilter == null) return true;
        return state == null ? !archivedFilter : state.archived() == archivedFilter;
    }

    private static OwnedState stateOf(Map<String, Map<UUID, OwnedState>> states, Conversation conversation, UUID actor) {
        return states.getOrDefault(conversation.getId(), Map.of()).get(actor);
    }

    /** Monta o item com preview e não lidas; cursor depois da última mensagem gravada é erro */
    private ConversationListItemResponseDTO listItem(Conversation conversation, OwnedState state,
            ConversationMessageHead head, Long unread, long syncVersion, CommunityStatus communityStatus) {
        if (state != null && (head == null || state.lastReadSequence() > head.persistedHeadSequence())) {
            throw new BaselineUnavailableException();
        }

        // preview da última mensagem
        LatestMessage latest = head == null ? null : head.lastMessage();
        Preview preview = latest == null ? null : new Preview(
                latest.id(), latest.sequence(), latest.senderId(), latest.content(), latest.edited(), latest.deleted(), latest.timestamp());

        // última interação: a mais recente entre a conversa e a última mensagem
        Instant interaction = conversation.getLastInteractionAt();
        if (latest != null && latest.timestamp() != null && (interaction == null || latest.timestamp().isAfter(interaction))) {
            interaction = latest.timestamp();
        }

        // campos pessoais ficam nulos quando não há estado (chatbot)
        return new ConversationListItemResponseDTO(conversation.getId(), clock.epoch(), syncVersion, conversation.getConversationType(),
                conversation.getParticipantIds(), conversation.getCreatedBy(), conversation.getManagerUserId(), conversation.getTitle(),
                conversation.getCommunityId(), communityStatus, conversation.getStatus(), conversation.getStartedAt(), interaction,
                state == null ? null : state.lastReadSequence(), state == null ? null : unread,
                state == null ? null : state.muted(), state == null ? null : state.archived(),
                state == null ? null : state.muted() || state.archived(), preview);
    }
}
