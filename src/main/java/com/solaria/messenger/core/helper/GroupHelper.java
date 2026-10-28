package com.solaria.messenger.core.helper;

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
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;

import static com.solaria.messenger.core.helper.PureRulesHelper.applyOptimistic;
import static com.solaria.messenger.core.helper.PureRulesHelper.nextVersion;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireConversationActive;

/**
 * Confere a gestão de grupos e grava as mudanças de participantes e de gestor.
 *
 * <p>
 * As operações de gestão de grupo usam esta classe: carregar o grupo, exigir que o ator seja o gestor ativo, validar
 * sucessor e remoção, e gravar com CAS. Quando a versão muda, a escrita é refeita sobre o grupo mais recente.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> grava participantes e gestor com CAS.</li>
 *     <li>{@link ConversationAccessHelper} -> confere participação e comunidade ativa.</li>
 *     <li>{@link LookupHelper} -> carrega o grupo e a comunidade pai.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #managedConversation(String)} -> carrega a conversa e exige que seja um grupo.</li>
 *     <li>{@link #communityFor(Conversation)} -> devolve a comunidade do grupo, se houver.</li>
 *     <li>{@link #requireManageable(Conversation, UUID)} -> exige gestor atual em grupo e comunidade ativos.</li>
 *     <li>{@link #validateSuccessor(Conversation, UUID, UUID)} -> valida o sucessor do gestor.</li>
 *     <li>{@link #validateRemoval(Conversation, UUID, UUID, UUID)} -> valida a remoção de um participante.</li>
 *     <li>{@link #writeParticipants(Conversation, Set, UUID)} -> grava participantes e gestor com CAS.</li>
 *     <li>{@link #writeManager(Conversation, UUID)} -> grava só o novo gestor com CAS.</li>
 *     <li>{@link #mutateGroup(Conversation, Function)} -> aplica uma escrita com nova tentativa.</li>
 * </ul>
 */
@Component
public class GroupHelper {

    private final ConversationRepository conversationRepository;
    private final ConversationAccessHelper access;
    private final LookupHelper lookup;

    public GroupHelper(ConversationRepository conversations, ConversationAccessHelper access, LookupHelper lookup) {
        this.conversationRepository = conversations;
        this.access = access;
        this.lookup = lookup;
    }

    /**
     * Carrega a conversa e exige que seja um grupo.
     *
     * @param id identificador da conversa.
     * @return conversa do tipo grupo.
     * @throws BusinessRuleException caso a conversa não seja um grupo.
     */
    public Conversation managedConversation(String id) {
        Conversation conversation = lookup.requireConversation(id);
        if (conversation.getConversationType() != ConversationType.GROUP) {
            throw new BusinessRuleException("Só conversas em grupo aceitam gestão de participantes.");
        }
        return conversation;
    }

    /**
     * Devolve a comunidade do grupo.
     *
     * @param conversation grupo consultado.
     * @return comunidade do grupo, ou nulo quando o grupo está fora de comunidade.
     */
    public ProjectCommunity communityFor(Conversation conversation) {
        return conversation.getCommunityId() == null ? null : lookup.requireParentCommunity(conversation.getCommunityId());
    }

    /**
     * Confere que o ator é o gestor atual de um grupo ativo, numa comunidade ativa.
     *
     * <p>
     * Confere a participação, o papel de gestor, o estado da conversa e o estado da comunidade, nessa ordem.
     * </p>
     *
     * @param conversation grupo consultado.
     * @param actor identificador do ator.
     * @throws UnauthorizedAccessException caso o ator não seja o gestor atual.
     * @throws BusinessRuleException caso o grupo ou a comunidade estejam inativos.
     */
    public void requireManageable(Conversation conversation, UUID actor) {
        authorizeManagement(conversation, actor);
        requireConversationActive(conversation);
        access.requireCommunityActive(conversation);
    }

    /** O ator precisa participar e ser o gestor do grupo */
    private void authorizeManagement(Conversation conversation, UUID actor) {
        access.requireParticipant(conversation.getId(), actor);
        if (!actor.equals(conversation.getManagerUserId())) {
            throw new UnauthorizedAccessException("A gestão exige o gestor atual do grupo.");
        }
    }

    /**
     * Confere que o sucessor pode assumir a gestão.
     *
     * <p>
     * O sucessor precisa ser outro participante do grupo. Em subgrupo, também precisa ser membro da comunidade.
     * </p>
     *
     * @param conversation grupo consultado.
     * @param departingManager gestor que sai.
     * @param successor identificador do sucessor.
     * @throws SuccessorIneligibleException caso o sucessor seja nulo, seja o gestor que sai, não participe do grupo ou
     *     não seja membro da comunidade.
     */
    public void validateSuccessor(Conversation conversation, UUID departingManager, UUID successor) {
        if (successor == null || successor.equals(departingManager) || !conversation.hasParticipant(successor)) {
            throw new SuccessorIneligibleException();
        }
        ProjectCommunity community = communityFor(conversation);
        if (community != null && !community.isMember(successor)) {
            throw new SuccessorIneligibleException();
        }
    }

    /**
     * Confere se a remoção de um participante deixa o grupo consistente.
     *
     * <p>
     * O grupo precisa ter um gestor que ainda participa. Quando o gestor sai, precisa de sucessor. A exceção é a
     * remoção administrativa do último participante, que deixa o grupo vazio. Se houver sucessor, ele passa pela
     * validação de sucessor.
     * </p>
     *
     * @param conversation grupo consultado.
     * @param actor identificador de quem pede a remoção.
     * @param target participante a remover.
     * @param successor sucessor do gestor, se houver.
     * @throws GroupManagerUnavailableException caso o grupo não tenha gestor que ainda participe.
     * @throws SuccessorRequiredException caso o gestor saia sem sucessor.
     */
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

    /**
     * Grava participantes e gestor com CAS.
     *
     * <p>
     * Grupo com participantes precisa de gestor que participe e, em subgrupo, seja membro da comunidade.
     * </p>
     *
     * @param current grupo na versão lida.
     * @param participants participantes que ficam no grupo.
     * @param manager gestor que fica no grupo.
     * @return resultado do CAS, em que {@code 1} indica gravação.
     * @throws GroupManagerUnavailableException caso o gestor não seja válido.
     */
    public long writeParticipants(Conversation current, Set<UUID> participants, UUID manager) {
        if (!participants.isEmpty() && (manager == null || !participants.contains(manager))) {
            throw new GroupManagerUnavailableException();
        }
        ProjectCommunity parent = communityFor(current);
        if (manager != null && parent != null && !parent.isMember(manager)) {
            throw new GroupManagerUnavailableException();
        }
        return conversationRepository.updateParticipantsIfCurrent(current.getId(), current.getVersion(),
                participants, manager, Instant.now(), nextVersion(current.getVersion()));
    }

    /**
     * Grava o novo gestor com CAS.
     *
     * @param current grupo na versão lida.
     * @param manager novo gestor.
     * @return resultado do CAS, em que {@code 1} indica gravação.
     */
    public long writeManager(Conversation current, UUID manager) {
        return conversationRepository.updateManagerIfCurrent(current.getId(), current.getVersion(),
                manager, Instant.now(), nextVersion(current.getVersion()));
    }

    /**
     * Aplica uma escrita sobre o grupo, refazendo a tentativa se a versão mudar.
     *
     * @param initial grupo lido antes da escrita.
     * @param mutation escrita a aplicar. Devolve {@code null} quando não há nada a gravar.
     * @return grupo depois da escrita, ou o mais recente lido quando nada foi gravado.
     * @throws ConcurrentUpdateException caso o CAS não consiga gravar depois das tentativas.
     */
    public Conversation mutateGroup(Conversation initial, Function<Conversation, Long> mutation) {
        return applyOptimistic(initial, mutation, () -> managedConversation(initial.getId()),
                ConcurrentUpdateException::new);
    }
}
