package com.solaria.messenger.core.service.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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
import com.solaria.messenger.core.service.ConversationUserStateService;
import com.solaria.messenger.core.service.state.OwnedState;

/**
 * Service que monta os itens da lista de conversas
 *
 * <p>
 * Gera, em lote, o mesmo item usado na consulta REST e nos eventos privados: preview da última mensagem,
 * não lidas, mute e archive de cada user.
 * </p>
 * <ul>
 *     <li>{@link #project(List, Map, Map, Boolean)} -> monta os itens de cada destinatário autorizado.</li>
 * </ul>
 */
@Service
public class ConversationProjectionService {

    private final ConversationUserStateService personalStates;
    private final MessageRepository messages;
    private final ConversationSyncClock clock;

    public ConversationProjectionService(ConversationUserStateService states, MessageRepository messages,
            ConversationSyncClock clock) {
        this.personalStates = states;
        this.messages = messages;
        this.clock = clock;
    }

    /** Monta os itens por destinatário; {@code archivedFilter} nulo não filtra pelo archive pessoal */
    public Map<UUID, List<ConversationListItemResponseDTO>> project(List<Conversation> conversations,
            Map<String, Set<UUID>> authorized, Map<String, ProjectCommunity> parents, Boolean archivedFilter) {
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
        long version = clock.nextVersion();

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
