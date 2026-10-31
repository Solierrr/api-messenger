package com.solaria.messenger.core.usecase.conversation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.DirectConversationRequestDTO;
import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.exception.DirectIdentityInconsistentException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAdmissionHelper;
import com.solaria.messenger.core.helper.ConversationStateHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.state.OwnedState;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.directKey;
import static com.solaria.messenger.core.helper.PureRulesHelper.newConversation;
import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

/**
 * Caso de uso que cria a conversa direta entre dois usuários, ou reaproveita a que já existe.
 *
 * <p>
 * A direta é única por par. A chave {@code directKey} vem dos dois ids ordenados, então a ordem do pedido não muda o
 * resultado. Se a direta já existe, ela volta sem trocar histórico nem preferências. Se duas requisições criam o
 * mesmo par ao mesmo tempo, a que perde usa a direta criada pela outra.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> busca e grava a conversa.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link UserAccessHelper} -> confere se os dois users estão ativos.</li>
 *     <li>{@link ConversationStateHelper} -> valida o estado pessoal dos participantes.</li>
 *     <li>{@link ConversationAdmissionHelper} -> grava conversa e estados na mesma transação.</li>
 *     <li>{@link MessageRepository} -> consulta a última sequência gravada.</li>
 *     <li>{@link NotificationHelper} -> avisa os clientes quando algo muda.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(DirectConversationRequestDTO)} -> cria a direta do par ou reaproveita a existente.</li>
 * </ul>
 */
@Service
public class CreateDirectConversationUseCase {

    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;
    private final ConversationStateHelper personalStates;
    private final ConversationAdmissionHelper admissions;
    private final MessageRepository messages;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public CreateDirectConversationUseCase(ConversationRepository conversations, RbacAuthorizationService rbac,
            UserAccessHelper users, ConversationStateHelper states,
            ConversationAdmissionHelper admissions, MessageRepository messages, NotificationHelper notifications,
            ConversationMutationGuard guard) {
        this.conversationRepository = conversations;
        this.rbac = rbac;
        this.users = users;
        this.personalStates = states;
        this.admissions = admissions;
        this.messages = messages;
        this.notifications = notifications;
        this.guard = guard;
    }

    /**
     * Cria a direta do par ou reaproveita a existente.
     *
     * <p>
     * A gravação roda dentro do {@link ConversationMutationGuard}. O aviso aos clientes sai depois, e só se algo foi
     * gravado.
     * </p>
     *
     * @param dto identificador do destinatário da direta.
     * @return direta criada ou reaproveitada.
     * @throws InvalidFieldException caso o destinatário seja nulo ou seja o próprio user.
     * @throws DirectIdentityInconsistentException caso a direta encontrada não seja exatamente o par.
     */
    public ConversationResponseDTO execute(DirectConversationRequestDTO dto) {
        // o array guarda, de dentro da lambda, se algo foi gravado e precisa ser avisado
        boolean[] changed = { false };

        ConversationResponseDTO response = guard.execute(() -> {
            // destinatário precisa ser outra pessoa, e os dois precisam estar ativos
            UUID actor = rbac.currentUserId();
            UUID recipient = dto.getRecipientId();
            if (recipient == null || actor.equals(recipient)) {
                throw new InvalidFieldException("Uma direta exige outro destinatário válido.");
            }
            users.requireActiveActor(actor);
            users.requireActiveUsers(Set.of(recipient));

            // já existe direta do par: reaproveita
            String key = directKey(List.of(actor, recipient));
            Conversation existing = conversationRepository.findByConversationTypeAndDirectKey(ConversationType.DIRECT, key)
                    .orElse(null);
            if (existing != null) return toResponse(reuseDirect(existing, actor, recipient));

            // não existe: cria uma nova
            Conversation conversation = newConversation(ConversationType.DIRECT, actor,
                    new LinkedHashSet<>(List.of(actor, recipient)));
            conversation.setDirectKey(key);
            try {
                Conversation saved = admissions.persistNew(conversation);
                changed[0] = true;
                return toResponse(saved);
            } catch (DuplicateKeyException collision) {
                // outra requisição criou o mesmo par ao mesmo tempo: usa a que ganhou
                Conversation winner = conversationRepository.findByConversationTypeAndDirectKey(ConversationType.DIRECT, key)
                        .orElseThrow(() -> collision);
                return toResponse(reuseDirect(winner, actor, recipient));
            }
        });

        if (changed[0]) notifications.publishConversationChanged(response.getId());
        return response;
    }

    /** Reaproveita a direta do par */
    private Conversation reuseDirect(Conversation conversation, UUID actor, UUID recipient) {
        requireDirectPair(conversation, actor, recipient);
        requireDirectStates(conversation);
        return conversation;
    }

    /** A direta encontrada precisa ser exatamente o par, fora de comunidade */
    private void requireDirectPair(Conversation conversation, UUID actor, UUID recipient) {
        if (conversation.getConversationType() != ConversationType.DIRECT || conversation.getCommunityId() != null
                || !Set.of(actor, recipient).equals(conversation.getParticipantIds())
                || !directKey(List.of(actor, recipient)).equals(conversation.getDirectKey())) {
            throw new DirectIdentityInconsistentException();
        }
    }

    /** Os dois lados precisam ter estado pessoal válido, sem cursor depois da última mensagem */
    private void requireDirectStates(Conversation conversation) {
        int head = messages.persistedHeadSequence(conversation.getId());
        for (UUID user : conversation.getParticipantIds()) {
            OwnedState state = personalStates.requireOwned(user, conversation.getId());
            if (state.lastReadSequence() > head) throw new BaselineUnavailableException();
        }
    }
}
