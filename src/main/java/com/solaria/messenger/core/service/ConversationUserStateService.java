package com.solaria.messenger.core.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.ConversationStatePatchRequestDTO;
import com.solaria.messenger.core.contract.request.PatchField;
import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.service.state.AuthorizedStateContext;
import com.solaria.messenger.core.service.state.OwnedState;
import com.solaria.messenger.core.service.state.StateChange;

/**
 * Service do estado pessoal do user em cada conversa (cursor de leitura, mute, archive)
 *
 * <p>
 * Lê e altera só o estado do próprio user. Estado com baseline inválido ou duplicado vira erro;
 * nada é corrigido automaticamente.
 * </p>
 * <ul>
 *     <li>{@link #findOwned(UUID, Set)} -> estados do user nas conversas informadas.</li>
 *     <li>{@link #requireOwned(UUID, String)} -> estado do user numa conversa, ou erro.</li>
 *     <li>{@link #findOwnedBatch(Set, Set)} -> estados de vários users em várias conversas.</li>
 *     <li>{@link #patchOwned(AuthorizedStateContext, ConversationStatePatchRequestDTO)} -> aplica o PATCH no estado.</li>
 * </ul>
 */
@Service
public class ConversationUserStateService {

    private final ConversationUserStateRepository repository;

    public ConversationUserStateService(ConversationUserStateRepository repository) {
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

    /** Aplica só os campos que mudam; o cursor nunca volta e nunca passa da última mensagem gravada */
    public StateChange patchOwned(AuthorizedStateContext context, ConversationStatePatchRequestDTO patch) {
        // valida o corpo do PATCH e o contexto vindo do chamador
        PatchField<Integer> cursor = patch == null ? null : patch.lastReadSequence();
        PatchField<Boolean> muted = patch == null ? null : patch.muted();
        PatchField<Boolean> archived = patch == null ? null : patch.archived();
        validatePatch(cursor, muted, archived);
        requireAuthorizedContext(context);
        if (cursor.present() && cursor.value() > context.persistedHeadSequence()) {
            throw new InvalidFieldException("lastReadSequence não pode exceder o maior sequence persistido.");
        }

        // carrega o estado atual; cursor ou baseline depois da última mensagem indica estado quebrado
        ConversationUserState persisted = repository
                .findByConversationIdAndUserId(context.conversationId(), context.actor())
                .orElseThrow(BaselineUnavailableException::new);
        OwnedState current = toOwnedState(persisted);
        if (context.persistedHeadSequence() < current.lastReadSequence()
                || context.persistedHeadSequence() < current.baselineSequence()) {
            throw new BaselineUnavailableException();
        }

        // descobre o que muda de fato (cursor só anda para frente); nada mudou -> não grava
        boolean changesCursor = cursor.present() && cursor.value() > current.lastReadSequence();
        boolean changesMuted = muted.present() && !muted.value().equals(current.muted());
        boolean changesArchived = archived.present() && !archived.value().equals(current.archived());
        if (!changesCursor && !changesMuted && !changesArchived) {
            return new StateChange(current, false);
        }

        // grava os campos presentes e relê o estado final
        boolean changed = repository.patchPartial(context.conversationId(), context.actor(),
                valueIfPresent(cursor), valueIfPresent(muted), valueIfPresent(archived));
        ConversationUserState after = repository
                .findByConversationIdAndUserId(context.conversationId(), context.actor())
                .orElseThrow(BaselineUnavailableException::new);
        return new StateChange(toOwnedState(after), changed);
    }

    /** Pelo menos um campo, sem nulos, e cursor não negativo */
    private static void validatePatch(PatchField<Integer> cursor,
            PatchField<Boolean> muted, PatchField<Boolean> archived) {
        if (cursor == null || muted == null || archived == null) {
            throw new InvalidFieldException("O PATCH de estado é inválido.");
        }
        if (!cursor.present() && !muted.present() && !archived.present()) {
            throw new InvalidFieldException("Informe ao menos um campo de estado.");
        }
        if ((cursor.present() && cursor.value() == null)
                || (muted.present() && muted.value() == null)
                || (archived.present() && archived.value() == null)) {
            throw new InvalidFieldException("Campos de estado não podem ser nulos ou ter tipo inválido.");
        }
        if (cursor.present() && cursor.value() < 0) {
            throw new InvalidFieldException("lastReadSequence deve ser um inteiro não negativo.");
        }
    }

    /** O chamador precisa mandar conversa, user e última sequência válidos */
    private static void requireAuthorizedContext(AuthorizedStateContext context) {
        if (context == null || context.conversationId() == null || context.actor() == null
                || context.persistedHeadSequence() < 0) {
            throw new BaselineUnavailableException();
        }
    }

    /** Campo omitido vai como {@code null} para o repository, que então não mexe nele */
    private static <T> T valueIfPresent(PatchField<T> field) {
        return field.present() ? field.value() : null;
    }

    /** Converte o documento em estado validado; baseline inválido vira erro */
    private static OwnedState toOwnedState(ConversationUserState state) {
        if (state == null || !state.hasValidBaseline()) throw new BaselineUnavailableException();
        return new OwnedState(state.getConversationId(), state.getUserId(), state.getBaselineSequence(),
                state.getLastReadSequence(), state.getMuted(), state.getArchived());
    }
}
