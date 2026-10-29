package com.solaria.messenger.core.helper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.state.OwnedState;
import com.solaria.messenger.core.support.ConversationSyncClock;

/**
 * Lê e valida o estado pessoal de cada user em cada conversa.
 *
 * <p>
 * O estado pessoal guarda o cursor de leitura, as preferências de silenciar e arquivar, e a sequência inicial
 * (baseline) da admissão. Os métodos de leitura nunca criam estado. Estado duplicado ou com baseline inválido é tratado
 * como erro, porque indica dado quebrado. A única escrita é a marca de saída: quando o user perde a conversa, o estado
 * recebe a versão daquele momento para o catch-up saber até onde devolver as mensagens.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationUserStateRepository} -> lê os estados gravados e grava a marca de saída.</li>
 *     <li>{@link ConversationSyncClock} -> fornece a versão da saída.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #findOwned(UUID, Set)} -> devolve os estados do user em um conjunto de conversas.</li>
 *     <li>{@link #requireOwned(UUID, String)} -> devolve o estado do user em uma conversa.</li>
 *     <li>{@link #findOwnedBatch(Set, Set)} -> devolve os estados de vários users em várias conversas.</li>
 *     <li>{@link #toOwnedState(ConversationUserState)} -> converte o documento em estado validado.</li>
 *     <li>{@link #markLeft(UUID, String)} -> registra a versão em que o user perdeu a conversa.</li>
 * </ul>
 */
@Component
public class ConversationStateHelper {

    private static final Logger log = LoggerFactory.getLogger(ConversationStateHelper.class);
    private final ConversationUserStateRepository repository;
    private final ConversationSyncClock clock;

    public ConversationStateHelper(ConversationUserStateRepository repository, ConversationSyncClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Registra a versão em que o user perdeu a participação na conversa.
     *
     * <p>
     * Deve ser chamado depois que a participação foi gravada, no mesmo ponto em que o aviso de remoção é publicado. A
     * primeira marca vale. Repetir a chamada, como no reenvio do DELETE da comunidade, não a move. Conversa sem estado
     * pessoal (chatbot) não tem o que marcar. A readmissão limpa a marca. A participação já foi gravada, então uma
     * falha técnica só gera log. Assim, o aviso de remoção e a derrubada das sessões, que vêm em seguida, não ficam
     * para trás.
     * </p>
     *
     * @param user user que perdeu a conversa.
     * @param conversationId identificador da conversa.
     */
    public void markLeft(UUID user, String conversationId) {
        // shortcut: a versão sai do relógio depois do CAS de participantes, sem transação, então uma mensagem de outro
        // pod nessa janela de milissegundos pode entrar ou ficar fora do catch-up de quem saiu, evoluir se o corte
        // tiver de ser exato
        try {
            repository.markLeft(conversationId, user, clock.nextVersion());
        } catch (RuntimeException failure) {
            log.error("Falha ao marcar a saída do user {} da conversa {}; o catch-up não devolverá as mensagens até a saída",
                    user, conversationId, failure);
        }
    }

    /**
     * Devolve os estados do user nas conversas informadas.
     *
     * @param actor identificador do user.
     * @param conversationIds identificadores das conversas.
     * @return mapa de id da conversa para o estado. Vazio quando não há conversas.
     * @throws BaselineUnavailableException caso o user ou as conversas sejam nulos, ou haja estado duplicado ou
     *     inválido.
     */
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

    /**
     * Devolve o estado do user em uma conversa da qual ele participa.
     *
     * @param actor identificador do user.
     * @param conversationId identificador da conversa.
     * @return estado validado.
     * @throws BaselineUnavailableException caso o estado não exista ou não corresponda ao user e à conversa.
     */
    public OwnedState requireOwned(UUID actor, String conversationId) {
        OwnedState state = findOwned(actor, Set.of(conversationId)).get(conversationId);
        if (state == null || !actor.equals(state.userId()) || !conversationId.equals(state.conversationId())) {
            throw new BaselineUnavailableException();
        }
        return state;
    }

    /**
     * Devolve os estados de vários users em várias conversas.
     *
     * <p>
     * Usa a mesma validação da leitura REST, então estado duplicado ou inválido vira erro.
     * </p>
     *
     * @param actors identificadores dos users.
     * @param conversations identificadores das conversas.
     * @return mapa de id da conversa para o mapa de id do user para o estado.
     * @throws BaselineUnavailableException caso haja estado duplicado ou inválido.
     */
    public Map<String, Map<UUID, OwnedState>> findOwnedBatch(Set<UUID> actors, Set<String> conversations) {
        Map<String, Map<UUID, OwnedState>> result = new LinkedHashMap<>();
        for (ConversationUserState persisted : repository.findByUsersAndConversations(actors, conversations)) {
            OwnedState state = toOwnedState(persisted);
            if (result.computeIfAbsent(state.conversationId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(state.userId(), state) != null) throw new BaselineUnavailableException();
        }
        return result;
    }

    /**
     * Converte o documento de estado em estado validado.
     *
     * @param state documento lido do banco.
     * @return estado com a baseline, o cursor e as preferências.
     * @throws BaselineUnavailableException caso o documento seja nulo ou sem baseline válida.
     */
    public static OwnedState toOwnedState(ConversationUserState state) {
        if (state == null || !state.hasValidBaseline()) throw new BaselineUnavailableException();
        return new OwnedState(state.getConversationId(), state.getUserId(), state.getBaselineSequence(),
                state.getLastReadSequence(), state.getMuted(), state.getArchived());
    }
}
