package com.solaria.messenger.core.helper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.state.OwnedState;

@Component
public class ConversationStateHelper {

    private final ConversationUserStateRepository repository;

    public ConversationStateHelper(ConversationUserStateRepository repository) {
        this.repository = repository;
    }

    /** Estados do user nas conversas informadas; estado duplicado ou inválido gera erro */
    public Map<String, OwnedState> findOwned(UUID actor, Set<String> conversationIds) {
        if (actor == null || conversationIds == null) throw new BaselineUnavailableException();
        if (conversationIds.isEmpty()) return Map.of();

        Map<String, OwnedState> result = new LinkedHashMap<>();
        for (ConversationUserState persisted : repository.findByUserIdAndConversationIds(actor, conversationIds)) {
            OwnedState state = toOwnedState(persisted);
            if (result.putIfAbsent(state.conversationId(), state) != null) throw new BaselineUnavailableException();
        }
        return Map.copyOf(result);
    }

    /** Estado do user numa conversa que ele já participa; a leitura nunca cria estado */
    public OwnedState requireOwned(UUID actor, String conversationId) {
        OwnedState state = findOwned(actor, Set.of(conversationId)).get(conversationId);
        if (state == null || !actor.equals(state.userId()) || !conversationId.equals(state.conversationId())) {
            throw new BaselineUnavailableException();
        }
        return state;
    }

    /** Estados de vários users em várias conversas, com a mesma validação da leitura REST */
    public Map<String, Map<UUID, OwnedState>> findOwnedBatch(Set<UUID> actors, Set<String> conversations) {
        Map<String, Map<UUID, OwnedState>> result = new LinkedHashMap<>();
        for (ConversationUserState persisted : repository.findByUsersAndConversations(actors, conversations)) {
            OwnedState state = toOwnedState(persisted);
            if (result.computeIfAbsent(state.conversationId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(state.userId(), state) != null) throw new BaselineUnavailableException();
        }
        return result;
    }

    /** Converte o documento em estado validado; baseline inválido vira erro */
    public static OwnedState toOwnedState(ConversationUserState state) {
        if (state == null || !state.hasValidBaseline()) throw new BaselineUnavailableException();
        return new OwnedState(state.getConversationId(), state.getUserId(), state.getBaselineSequence(),
                state.getLastReadSequence(), state.getMuted(), state.getArchived());
    }
}
