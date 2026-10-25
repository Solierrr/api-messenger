package com.solaria.messenger.core.service.conversation;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.solaria.messenger.core.contract.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.core.contract.request.DirectConversationRequestDTO;
import com.solaria.messenger.core.contract.request.GroupConversationRequestDTO;
import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.RejectedParticipantDTO;
import com.solaria.messenger.core.exception.BaselineUnavailableException;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.DirectIdentityInconsistentException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.PersistenceUncertainException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.ConversationUserStateService;
import com.solaria.messenger.core.service.OptimisticMutation;
import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.core.service.state.OwnedState;

import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

/**
 * Service de criação de conversas
 *
 * <p>
 * Cria diretas, grupos e conversas com o chatbot. A direta de um par é única: se já existir, é reaproveitada
 * (e reativada se estiver desativada). A gravação da conversa com os estados fica em {@link ConversationAdmissionService}.
 * </p>
 * <ul>
 *     <li>{@link #createDirectConversation(DirectConversationRequestDTO)} -> cria ou reaproveita a direta entre dois users.</li>
 *     <li>{@link #createGroupConversation(GroupConversationRequestDTO)} -> cria um grupo avulso.</li>
 *     <li>{@link #createCommunityGroupConversation(String, String, Set)} -> cria um subgrupo de comunidade.</li>
 *     <li>{@link #createChatbotConversation(ChatbotConversationRequestDTO)} -> cria a conversa do user com o chatbot.</li>
 * </ul>
 */
@Service
public class ConversationCreationService {

    private final ConversationRepository conversationRepository;
    private final ConversationAdmissionService admissions;
    private final RbacAuthorizationService rbac;
    private final UserAccessService userAccessService;
    private final ConversationAuthorizationService resourceAccess;
    private final ConversationUserStateService personalStates;
    private final MessageRepository messages;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;

    public ConversationCreationService(ConversationRepository conversations, ConversationAdmissionService admissions,
            RbacAuthorizationService rbac, UserAccessService users, ConversationAuthorizationService authorization,
            ConversationUserStateService states, MessageRepository messages, ConversationChangeNotifier events,
            ConversationMutationGuard guard) {
        this.conversationRepository = conversations;
        this.admissions = admissions;
        this.rbac = rbac;
        this.userAccessService = users;
        this.resourceAccess = authorization;
        this.personalStates = states;
        this.messages = messages;
        this.stateEvents = events;
        this.guard = guard;
    }

    /** Cria a direta do par ou reaproveita a existente, sem trocar histórico nem preferências */
    public ConversationResponseDTO createDirectConversation(DirectConversationRequestDTO dto) {
        // o array guarda, de dentro da lambda, se algo foi gravado e precisa ser avisado
        boolean[] changed = { false };

        ConversationResponseDTO response = guard.execute(() -> {
            // destinatário precisa ser outra pessoa, e os dois precisam estar ativos
            UUID actor = rbac.currentUserId();
            UUID recipient = dto.getRecipientId();
            if (recipient == null || actor.equals(recipient)) {
                throw new InvalidFieldException("Uma direta exige outro destinatário válido.");
            }
            userAccessService.requireActiveActor(actor);
            userAccessService.requireActiveUsers(Set.of(recipient));

            // já existe direta do par: reaproveita
            String key = canonicalDirectKey(actor, recipient);
            Conversation existing = conversationRepository.findByConversationTypeAndDirectKey(ConversationType.DIRECT, key)
                    .orElse(null);
            if (existing != null) return toResponse(reuseDirect(existing, actor, recipient, changed));

            // não existe: cria uma nova
            Conversation conversation = newConversation(ConversationType.DIRECT, actor,
                    new LinkedHashSet<>(List.of(actor, recipient)));
            conversation.setDirectKey(key);
            try {
                Conversation saved = persistNew(conversation);
                changed[0] = true;
                return toResponse(saved);
            } catch (DuplicateKeyException collision) {
                // outra requisição criou o mesmo par ao mesmo tempo: usa a que ganhou
                Conversation winner = conversationRepository.findByConversationTypeAndDirectKey(ConversationType.DIRECT, key)
                        .orElseThrow(() -> collision);
                return toResponse(reuseDirect(winner, actor, recipient, changed));
            }
        });

        if (changed[0]) stateEvents.publishConversationChanged(response.getId());
        return response;
    }

    /** Cria um grupo avulso com os convidados elegíveis */
    public GroupCreationResponseDTO createGroupConversation(GroupConversationRequestDTO dto) {
        return createGroupAndPublish(null, dto.getTitle(), dto.getParticipantIds());
    }

    /** Cria um subgrupo de comunidade com os convidados elegíveis */
    public GroupCreationResponseDTO createCommunityGroupConversation(String communityId, String title,
            Set<UUID> requested) {
        return createGroupAndPublish(communityId, title, requested);
    }

    /** Cria a conversa do user com o chatbot, sem estado pessoal */
    public ConversationResponseDTO createChatbotConversation(ChatbotConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        Conversation conversation = newConversation(ConversationType.CHAT_BOT, currentUserId,
                new LinkedHashSet<>(List.of(currentUserId)));
        conversation.setUserType(dto.getUserType());
        conversation.setUserDetails(dto.getUserDetails());

        Conversation saved = conversationRepository.save(conversation);
        stateEvents.publishConversationChanged(saved.getId());
        return toResponse(saved);
    }

    /** Cria o grupo dentro do guard e avisa os participantes depois, fora dele */
    private GroupCreationResponseDTO createGroupAndPublish(String communityId, String title, Set<UUID> requested) {
        GroupCreationResponseDTO result = guard.execute(() ->
                createSelectedGroup(communityId, title, rbac.currentUserId(), requested));
        stateEvents.publishConversationChanged(result.conversation().getId());
        return result;
    }

    /** Monta o grupo com os elegíveis; só inelegibilidade conhecida vira rejeição, falha técnica sobe como erro */
    private GroupCreationResponseDTO createSelectedGroup(String communityId, String title, UUID creator,
            Set<UUID> requestedIds) {
        // criador ativo; em subgrupo, ele precisa ser membro de uma comunidade ativa
        userAccessService.requireActiveActor(creator);
        ProjectCommunity parent = communityId == null ? null : resourceAccess.requireCommunity(communityId);
        if (parent != null && !parent.isMember(creator)) {
            throw new UnauthorizedAccessException("O criador não participa da comunidade.");
        }
        if (parent != null && parent.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }

        // subgrupo sem convidados recebe todos os membros da comunidade
        Set<UUID> requested = new LinkedHashSet<>();
        if (requestedIds != null) requested.addAll(requestedIds);
        if (parent != null && requested.isEmpty()) requested.addAll(parent.memberIds());
        if (requested.contains(null)) throw new InvalidFieldException("Participantes não podem ser nulos.");

        // candidatos: convidados sem o criador e, em subgrupo, só membros da comunidade
        Set<UUID> candidates = new LinkedHashSet<>(requested);
        candidates.remove(creator);
        if (parent != null) candidates.retainAll(parent.memberIds());

        // aceitos: criador + candidatos elegíveis; o grupo precisa de pelo menos dois
        Set<UUID> accepted = new LinkedHashSet<>();
        accepted.add(creator);
        accepted.addAll(userAccessService.eligibleUsers(candidates));
        if (accepted.size() < 2) {
            throw new BusinessRuleException("Um grupo exige o criador e ao menos outro participante elegível.");
        }
        List<RejectedParticipantDTO> rejected = requested.stream().filter(user -> !accepted.contains(user))
                .map(user -> new RejectedParticipantDTO(user, "PARTICIPANT_NOT_ADMITTED")).toList();

        // grava o grupo e devolve aceitos e rejeitados
        Conversation conversation = newConversation(ConversationType.GROUP, creator, accepted);
        conversation.setTitle(title);
        conversation.setCommunityId(communityId);
        Conversation saved = persistNew(conversation);
        return new GroupCreationResponseDTO(toResponse(saved), saved.getParticipantIds(), rejected);
    }

    /** Reaproveita a direta do par; se estiver desativada, reativa com CAS */
    private Conversation reuseDirect(Conversation conversation, UUID actor, UUID recipient, boolean[] changed) {
        requireDirectPair(conversation, actor, recipient);
        requireDirectStates(conversation);
        if (conversation.getStatus() == ConversationStatus.ACTIVE) return conversation;
        if (conversation.getStatus() != ConversationStatus.DEACTIVATED) throw new ConcurrentUpdateException();

        // tenta reativar; erro técnico aqui deixa incerto se gravou
        Long next = OptimisticMutation.nextVersion(conversation.getVersion());
        long modified;
        try {
            modified = conversationRepository.reactivateDirectIfCurrent(conversation.getId(), conversation.getVersion(),
                    conversation.getDirectKey(), Set.of(actor, recipient), next);
        } catch (RuntimeException uncertain) {
            throw new PersistenceUncertainException("DIRECT_REACTIVATION_UNCERTAIN", conversation.getId(), uncertain);
        }
        if (modified == 1) {
            conversation.setStatus(ConversationStatus.ACTIVE);
            conversation.setVersion(next);
            changed[0] = true;
            return conversation;
        }

        // CAS não aplicou: outra requisição mexeu antes, então relê e só aceita se ela já estiver ativa
        Conversation current = resourceAccess.requireEntityById(conversation.getId());
        requireDirectPair(current, actor, recipient);
        requireDirectStates(current);
        if (current.getStatus() != ConversationStatus.ACTIVE) throw new ConcurrentUpdateException();
        return current;
    }

    /** A direta encontrada precisa ser exatamente o par, fora de comunidade */
    private void requireDirectPair(Conversation conversation, UUID actor, UUID recipient) {
        if (conversation.getConversationType() != ConversationType.DIRECT || conversation.getCommunityId() != null
                || !Set.of(actor, recipient).equals(conversation.getParticipantIds())
                || !canonicalDirectKey(actor, recipient).equals(conversation.getDirectKey())) {
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

    /** Grava conversa e estados juntos; erro de transação vira "persistência incerta" */
    private Conversation persistNew(Conversation conversation) {
        try {
            return admissions.createWithInitialStates(conversation);
        } catch (BusinessException | DuplicateKeyException | OptimisticLockingFailureException rejected) {
            throw rejected;
        } catch (TransactionException uncertain) {
            throw new PersistenceUncertainException("CONVERSATION_PERSISTENCE_UNCERTAIN", conversation.getId(), uncertain);
        } catch (RuntimeException failure) {
            throw new PersistenceUncertainException("DEPENDENCY_UNAVAILABLE", conversation.getId(), failure);
        }
    }

    /** Monta uma conversa ativa com id próprio; em grupo, o criador começa como gestor */
    private Conversation newConversation(ConversationType type, UUID createdBy, Set<UUID> participants) {
        Instant now = Instant.now();
        Conversation conversation = new Conversation();
        conversation.setId(new ObjectId().toHexString());
        conversation.setConversationType(type);
        conversation.setParticipantIds(participants);
        conversation.setCreatedBy(createdBy);
        if (type == ConversationType.GROUP) conversation.setManagerUserId(createdBy);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);
        return conversation;
    }

    /** Chave do par que não depende da ordem dos dois ids */
    private String canonicalDirectKey(UUID firstUserId, UUID secondUserId) {
        String first = firstUserId.toString();
        String second = secondUserId.toString();
        return first.compareTo(second) < 0 ? first + "|" + second : second + "|" + first;
    }
}
