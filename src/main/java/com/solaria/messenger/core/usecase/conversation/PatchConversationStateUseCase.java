package com.solaria.messenger.core.usecase.conversation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.ConversationStatePatchRequestDTO;
import com.solaria.messenger.core.contract.request.PatchField;
import com.solaria.messenger.core.contract.response.ConversationStateResponseDTO;
import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.state.AuthorizedStateContext;
import com.solaria.messenger.core.state.OwnedState;
import com.solaria.messenger.core.state.StateChange;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

import static com.solaria.messenger.core.helper.ConversationStateHelper.toOwnedState;

@Service
public class PatchConversationStateUseCase {

    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final ConversationUserStateRepository repository;
    private final MessageRepository messages;
    private final ConversationSyncClock clock;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public PatchConversationStateUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access,
            ConversationUserStateRepository repository, MessageRepository messages, ConversationSyncClock clock,
            NotificationHelper notifications, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.access = access;
        this.repository = repository;
        this.messages = messages;
        this.clock = clock;
        this.notifications = notifications;
        this.guard = guard;
    }

    /** Aplica o PATCH; se nada mudou, não grava nem avisa */
    public ConversationStateResponseDTO execute(String id, ConversationStatePatchRequestDTO patch) {
        UUID actor = rbac.currentUserId();
        boolean[] changed = { false };
        long version = clock.nextVersion();

        ConversationStateResponseDTO response = guard.execute(() -> access.withAccess(id, actor, current -> {
            if (current.getConversationType() == ConversationType.CHAT_BOT) throw new BusinessRuleException("CHAT_BOT não possui estado pessoal.");

            // aplica o PATCH usando a última sequência gravada como limite do cursor
            int head = messages.persistedHeadSequence(id);
            StateChange change = patchOwned(new AuthorizedStateContext(id, actor, head), patch);
            changed[0] = change.changed();

            // recalcula as não lidas a partir do novo cursor e monta a resposta
            OwnedState state = change.state();
            long unread = messages.countUnreadByConversationCursors(actor, Map.of(id, state.lastReadSequence())).get(id);
            return new ConversationStateResponseDTO(id, clock.epoch(), version, state.lastReadSequence(),
                    unread, state.muted(), state.archived(), state.muted() || state.archived());
        }));

        // estado pessoal: só o próprio user é avisado
        if (changed[0]) notifications.publishConversationChanged(id, Set.of(actor));
        return response;
    }

    /** Aplica só os campos que mudam; o cursor nunca volta e nunca passa da última mensagem gravada */
    private StateChange patchOwned(AuthorizedStateContext context, ConversationStatePatchRequestDTO patch) {
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
}
