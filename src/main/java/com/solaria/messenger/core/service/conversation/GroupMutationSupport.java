package com.solaria.messenger.core.service.conversation;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.exception.SuccessorIneligibleException;
import com.solaria.messenger.core.exception.SuccessorRequiredException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.OptimisticMutation;

/**
 * Component com as regras de gestor/sucessor e as escritas de grupo
 *
 * <p>
 * Usado pela gestão do grupo ({@link GroupManagementService}) e pela cascata da comunidade.
 * As escritas usam CAS: só gravam se a versão lida ainda for a atual.
 * </p>
 * <ul>
 *     <li>{@link #managedConversation(String)} -> carrega a conversa e exige que seja grupo.</li>
 *     <li>{@link #communityFor(Conversation)} -> comunidade do grupo, ou null.</li>
 *     <li>{@link #validateSuccessor(Conversation, UUID, UUID)} -> valida o sucessor do gestor.</li>
 *     <li>{@link #validateRemoval(Conversation, UUID, UUID, UUID)} -> valida a remoção de um participante.</li>
 *     <li>{@link #writeParticipants(Conversation, Set, UUID)} -> grava participantes e gestor.</li>
 *     <li>{@link #writeManager(Conversation, UUID)} -> grava o novo gestor.</li>
 *     <li>{@link #mutateGroup(Conversation, Function)} -> aplica a escrita e tenta de novo se a versão mudou.</li>
 * </ul>
 */
@Component
public class GroupMutationSupport {

    private final ConversationRepository conversationRepository;
    private final ConversationAuthorizationService resourceAccess;

    public GroupMutationSupport(ConversationRepository conversations, ConversationAuthorizationService authorization) {
        this.conversationRepository = conversations;
        this.resourceAccess = authorization;
    }

    /** Carrega a conversa e exige que seja um grupo */
    public Conversation managedConversation(String id) {
        Conversation conversation = resourceAccess.requireEntityById(id);
        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new BusinessRuleException("Só conversas em grupo aceitam gestão de participantes.");
        }
        return conversation;
    }

    /** Comunidade atual do grupo, ou {@code null} fora de comunidade */
    public ProjectCommunity communityFor(Conversation conversation) {
        return conversation.getCommunityId() == null ? null : resourceAccess.requireCommunity(conversation.getCommunityId());
    }

    /** Sucessor precisa ser outro participante e, em subgrupo, membro da comunidade */
    public void validateSuccessor(Conversation conversation, UUID departingManager, UUID successor) {
        if (successor == null || successor.equals(departingManager) || !conversation.hasParticipant(successor)) {
            throw new SuccessorIneligibleException();
        }
        ProjectCommunity community = communityFor(conversation);
        if (community != null && !community.isMember(successor)) {
            throw new SuccessorIneligibleException();
        }
    }

    /** Remover o gestor exige sucessor, menos quando outra pessoa remove o último participante */
    public void validateRemoval(Conversation conversation, UUID actor, UUID target, UUID successor) {
        if (!conversation.hasParticipant(target)) return;

        // o grupo precisa ter um gestor que ainda participa
        UUID manager = conversation.getManagerUserId();
        if (manager == null || !conversation.hasParticipant(manager)) {
            throw new GroupManagerUnavailableException();
        }

        // saída do gestor sem sucessor só vale quando o grupo vai ficar vazio por remoção de terceiro
        if (target.equals(manager)) {
            boolean emptyAdministrative = !actor.equals(target) && conversation.getParticipantIds().size() == 1;
            if (successor == null && !emptyAdministrative) throw new SuccessorRequiredException();
        }
        if (successor != null) validateSuccessor(conversation, target, successor);
    }

    /** Grava participantes e gestor com CAS; grupo com gente precisa de gestor participante e membro da comunidade */
    public long writeParticipants(Conversation current, Set<UUID> participants, UUID manager) {
        if (!participants.isEmpty() && (manager == null || !participants.contains(manager))) {
            throw new GroupManagerUnavailableException();
        }
        ProjectCommunity parent = communityFor(current);
        if (manager != null && parent != null && !parent.isMember(manager)) {
            throw new GroupManagerUnavailableException();
        }
        return conversationRepository.updateParticipantsIfCurrent(current.getId(), current.getVersion(),
                participants, manager, Instant.now(), OptimisticMutation.nextVersion(current.getVersion()));
    }

    /** Grava o novo gestor com CAS */
    public long writeManager(Conversation current, UUID manager) {
        return conversationRepository.updateManagerIfCurrent(current.getId(), current.getVersion(),
                manager, Instant.now(), OptimisticMutation.nextVersion(current.getVersion()));
    }

    /** Aplica a escrita; só tenta de novo quando o CAS confirmou que não gravou por mudança de versão */
    public Conversation mutateGroup(Conversation initial, Function<Conversation, Long> mutation) {
        return OptimisticMutation.apply(initial, mutation, () -> managedConversation(initial.getId()),
                ConcurrentUpdateException::new);
    }
}
