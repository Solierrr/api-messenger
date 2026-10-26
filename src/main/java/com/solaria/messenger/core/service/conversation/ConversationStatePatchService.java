package com.solaria.messenger.core.service.conversation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.ConversationStatePatchRequestDTO;
import com.solaria.messenger.core.contract.response.ConversationStateResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.ConversationUserStateService;
import com.solaria.messenger.core.service.state.AuthorizedStateContext;
import com.solaria.messenger.core.service.state.OwnedState;
import com.solaria.messenger.core.service.state.StateChange;

/**
 * Service que altera o estado pessoal do user numa conversa
 *
 * <p>
 * Muda cursor de leitura, mute e archive. A validação das regras fica em {@link ConversationUserStateService};
 * aqui só se orquestra e se avisa o próprio user quando algo mudou.
 * </p>
 * <ul>
 *     <li>{@link #patchOwnedState(String, ConversationStatePatchRequestDTO)} -> aplica o PATCH no estado pessoal.</li>
 * </ul>
 */
@Service
public class ConversationStatePatchService {

    private final RbacAuthorizationService rbac;
    private final ConversationAuthorizationService resourceAccess;
    private final ConversationUserStateService personalStates;
    private final MessageRepository messages;
    private final ConversationSyncClock clock;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;

    public ConversationStatePatchService(RbacAuthorizationService rbac, ConversationAuthorizationService authorization,
            ConversationUserStateService states, MessageRepository messages, ConversationSyncClock clock,
            ConversationChangeNotifier events, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.resourceAccess = authorization;
        this.personalStates = states;
        this.messages = messages;
        this.clock = clock;
        this.stateEvents = events;
        this.guard = guard;
    }

    /** Aplica o PATCH; se nada mudou, não grava nem avisa */
    public ConversationStateResponseDTO patchOwnedState(String id, ConversationStatePatchRequestDTO patch) {
        UUID actor = rbac.currentUserId();
        boolean[] changed = { false };
        long version = clock.nextVersion();

        ConversationStateResponseDTO response = guard.execute(() -> resourceAccess.withAccess(id, actor, current -> {
            if (current.getConversationType() == ConversationType.CHAT_BOT) throw new BusinessRuleException("CHAT_BOT não possui estado pessoal.");

            // aplica o PATCH usando a última sequência gravada como limite do cursor
            int head = messages.persistedHeadSequence(id);
            StateChange change = personalStates.patchOwned(new AuthorizedStateContext(id, actor, head), patch);
            changed[0] = change.changed();

            // recalcula as não lidas a partir do novo cursor e monta a resposta
            OwnedState state = change.state();
            long unread = messages.countUnreadByConversationCursors(actor, Map.of(id, state.lastReadSequence())).get(id);
            return new ConversationStateResponseDTO(id, clock.epoch(), version, state.lastReadSequence(),
                    unread, state.muted(), state.archived(), state.muted() || state.archived());
        }));

        // estado pessoal: só o próprio user é avisado
        if (changed[0]) stateEvents.publishConversationChanged(id, Set.of(actor));
        return response;
    }
}
