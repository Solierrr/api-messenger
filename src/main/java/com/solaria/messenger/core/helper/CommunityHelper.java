package com.solaria.messenger.core.helper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.GroupManagerUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MembershipCleanupRequiredException;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;

import static com.solaria.messenger.core.helper.PureRulesHelper.applyOptimistic;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireRemovalAuthority;
import static com.solaria.messenger.core.helper.PureRulesHelper.withoutUser;

/**
 * Grava a comunidade e conduz a cascata de remoção nos subgrupos.
 *
 * <p>
 * Reúne a escrita da comunidade com CAS e as operações que tocam os subgrupos: 
 * listar, validar o plano de remoção, transferir a gestão, tirar o membro dos grupos e barrar a readmissão enquanto houver resíduo. 
 * Toda remoção é validada inteira antes de qualquer escrita.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> busca subgrupos e confere resíduos.</li>
 *     <li>{@link UserAccessHelper} -> confere os sucessores ativos.</li>
 *     <li>{@link LookupHelper} -> recarrega a comunidade pai.</li>
 *     <li>{@link GroupHelper} -> valida e grava as mudanças em cada grupo.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #mutateCommunity(ProjectCommunity, Function)} -> aplica uma escrita na comunidade com nova tentativa.</li>
 *     <li>{@link #concurrentModification()} -> cria o erro de comunidade alterada durante a operação.</li>
 *     <li>{@link #subgroupMetadata(String)} -> lista os subgrupos no formato de listagem.</li>
 *     <li>{@link #validateCommunityRemoval(ProjectCommunity, UUID, UUID, Map)} -> valida o plano inteiro de remoção.</li>
 *     <li>{@link #prepareCommunityRemoval(ProjectCommunity, UUID, UUID, Map, List)} -> passa a gestão dos grupos antes da saída.</li>
 *     <li>{@link #cleanupCommunityRemoval(String, UUID, UUID)} -> tira o membro dos subgrupos depois da saída.</li>
 *     <li>{@link #requireNoResidualCommunityMembership(String, Set)} -> impede a readmissão enquanto houver resíduo.</li>
 * </ul>
 */
@Component
public class CommunityHelper {

    private final ConversationRepository conversationRepository;
    private final UserAccessHelper users;
    private final LookupHelper lookup;
    private final GroupHelper groups;

    public CommunityHelper(ConversationRepository conversations, UserAccessHelper users, LookupHelper lookup,
            GroupHelper groups) {
        this.conversationRepository = conversations;
        this.users = users;
        this.lookup = lookup;
        this.groups = groups;
    }

    /**
     * Aplica uma escrita na comunidade com CAS.
     *
     * <p>
     * Se a versão mudou, relê a comunidade e tenta de novo, até as tentativas acabarem.
     * </p>
     *
     * @param initial comunidade lida antes da escrita.
     * @param mutation escrita a aplicar. Devolve {@code null} quando não há nada a gravar.
     * @return comunidade depois da escrita, ou a mais recente quando nada foi gravado.
     * @throws ConcurrentUpdateException caso o CAS não consiga gravar depois das tentativas.
     */
    public ProjectCommunity mutateCommunity(ProjectCommunity initial, Function<ProjectCommunity, Long> mutation) {
        return applyOptimistic(initial, mutation, () -> lookup.requireCommunity(initial.getId()),
                this::concurrentModification);
    }

    /**
     * Cria o erro de comunidade alterada durante a operação.
     *
     * @return erro de concorrência com a mensagem padrão da comunidade.
     */
    public BusinessException concurrentModification() {
        return new ConcurrentUpdateException("A comunidade mudou durante a operação.");
    }

    /**
     * Lista os subgrupos da comunidade no formato de listagem.
     *
     * @param communityId identificador da comunidade.
     * @return subgrupos, do mais recente para o mais antigo.
     */
    public List<SubgroupMetadataResponseDTO> subgroupMetadata(String communityId) {
        return findSubgroups(communityId).stream().map(ConversationMapper::toSubgroupMetadata).toList();
    }

    // cascata para os subgrupos

    /**
     * Valida o plano inteiro de remoção antes de qualquer escrita.
     *
     * <p>
     * Quando o user já saiu da comunidade, o sucessor não vale mais, e a operação só barra se ele ainda for gestor
     * de grupo com outros participantes. Quando ainda é membro, cada sucessor precisa apontar para um subgrupo
     * envolvido na remoção, e cada remoção de grupo é validada. Os sucessores que vão receber a gestão precisam estar
     * ativos.
     * </p>
     *
     * @param community comunidade atual.
     * @param actor identificador de quem pede a remoção.
     * @param target membro a remover.
     * @param successors sucessores por id de conversa.
     * @return subgrupos da comunidade, do mais recente para o mais antigo.
     * @throws GroupManagerUnavailableException caso o membro já tenha saído da comunidade e ainda seja gestor de grupo com outros participantes.
     * @throws InvalidFieldException caso um sucessor aponte para conversa fora da remoção.
     */
    public List<Conversation> validateCommunityRemoval(ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors) {
        List<Conversation> subgroups = findSubgroups(community.getId());

        // user já saiu da comunidade: sucessor não vale mais, só barra se ainda for gestor de grupo com outras pessoas
        if (!community.isMember(target)) {
            if (subgroups.stream().anyMatch(group -> isManagerWithOthers(group, target))) {
                throw new GroupManagerUnavailableException();
            }
            return subgroups;
        }

        // cada sucessor indicado precisa apontar para um subgrupo envolvido na remoção
        Map<String, Conversation> byId = new LinkedHashMap<>();
        subgroups.forEach(group -> byId.put(group.getId(), group));
        for (var entry : successors.entrySet()) {
            Conversation group = byId.get(entry.getKey());
            if (group == null || (!group.hasParticipant(target) && !entry.getValue().equals(group.getManagerUserId()))) {
                throw new InvalidFieldException("Sucessor indicado para conversa alheia à remoção.");
            }
        }

        // valida a remoção em cada grupo e junta os sucessores que vão receber a gestão
        Set<UUID> transfers = new LinkedHashSet<>();
        for (Conversation group : subgroups) {
            if (group.hasParticipant(target)) {
                UUID successor = successors.get(group.getId());
                groups.validateRemoval(group, actor, target, successor);
                if (successor != null && target.equals(group.getManagerUserId())) transfers.add(successor);
            }
        }

        // os sucessores precisam ser users ativos
        users.requireSuccessorsActive(transfers);
        return subgroups;
    }

    private static boolean isManagerWithOthers(Conversation group, UUID target) {
        return group.hasParticipant(target) && target.equals(group.getManagerUserId())
                && group.getParticipantIds().size() > 1;
    }

    /**
     * Passa a gestão dos grupos que o membro administra antes da saída da comunidade.
     *
     * <p>
     * Se o membro já saiu, não há nada a fazer. Cada grupo em que ele é gestor é atualizado com CAS, revalidando a
     * comunidade e a remoção na versão mais recente.
     * </p>
     *
     * @param community comunidade atual.
     * @param actor identificador de quem pede a remoção.
     * @param target membro a remover.
     * @param successors sucessores por id de conversa.
     * @param subgroups subgrupos validados antes.
     * @throws ConcurrentUpdateException caso o CAS não consiga gravar depois das tentativas.
     */
    public void prepareCommunityRemoval(ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors, List<Conversation> subgroups) {
        // só interessa grupo onde o user é gestor; se já saiu da comunidade, resta apenas a limpeza
        if (!community.isMember(target)) return;

        for (Conversation group : subgroups) {
            if (group.hasParticipant(target) && target.equals(group.getManagerUserId())) {
                groups.mutateGroup(group, latest -> transferManagement(latest, community, actor, target, successors));
            }
        }
    }

    /** Revalida na versão mais recente do grupo e da comunidade e passa a gestão; {@code null} = nada a gravar */
    private Long transferManagement(Conversation latest, ProjectCommunity community, UUID actor, UUID target,
            Map<String, UUID> successors) {
        if (!community.getId().equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
        ProjectCommunity parent = lookup.requireParentCommunity(community.getId());
        requireRemovalAuthority(parent, actor, target);
        if (!parent.isMember(target)) throw new ConcurrentUpdateException();
        if (!latest.hasParticipant(target) || !target.equals(latest.getManagerUserId())) return null;

        // grupo só com o user, removido por outra pessoa: o grupo fica vazio
        if (latest.getParticipantIds().size() == 1 && !actor.equals(target)) {
            return groups.writeParticipants(latest, Set.of(), null);
        }

        // senão, a gestão vai para o sucessor indicado
        UUID successor = successors.get(latest.getId());
        groups.validateSuccessor(latest, target, successor);
        return groups.writeManager(latest, successor);
    }

    /**
     * Tira o membro removido dos subgrupos, depois da saída da comunidade.
     *
     * <p>
     * Revalida na versão mais recente de cada grupo. Grupo que fica vazio perde o gestor. Se a limpeza falhar no
     * meio, o acesso já está bloqueado pela saída da comunidade.
     * </p>
     *
     * @param communityId identificador da comunidade.
     * @param actor identificador de quem pede a remoção.
     * @param target membro removido.
     * @throws ConcurrentUpdateException caso a comunidade ou o grupo mude e a tentativa não grave.
     */
    public void cleanupCommunityRemoval(String communityId, UUID actor, UUID target) {
        for (Conversation group : findSubgroups(communityId)) {
            if (!group.hasParticipant(target)) continue;

            groups.mutateGroup(group, latest -> {
                // revalida na versão mais recente; o user já não pode estar na comunidade
                if (!communityId.equals(latest.getCommunityId())) throw new ConcurrentUpdateException();
                ProjectCommunity parent = lookup.requireParentCommunity(communityId);
                requireRemovalAuthority(parent, actor, target);
                if (parent.isMember(target)) throw new ConcurrentUpdateException();
                if (!latest.hasParticipant(target)) return null;

                // remove o user; grupo que ficou vazio perde o gestor
                Set<UUID> survivors = withoutUser(latest.getParticipantIds(), target);
                return groups.writeParticipants(latest, survivors, survivors.isEmpty() ? null : latest.getManagerUserId());
            });
        }
    }

    /**
     * Barra a readmissão de usuários que ainda aparecem em algum subgrupo da comunidade.
     *
     * @param communityId identificador da comunidade.
     * @param users usuários que querem entrar.
     * @throws MembershipCleanupRequiredException caso algum usuário ainda participe de um subgrupo.
     */
    public void requireNoResidualCommunityMembership(String communityId, Set<UUID> users) {
        for (UUID user : users) {
            if (conversationRepository.existsByCommunityIdAndParticipantIdsContaining(communityId, user)) {
                throw new MembershipCleanupRequiredException();
            }
        }
    }

    /** Subgrupos da comunidade, do mais recente para o mais antigo */
    private List<Conversation> findSubgroups(String communityId) {
        return conversationRepository.findByCommunityIdAndConversationTypeOrderByLastInteractionAtDesc(
                communityId, ConversationType.GROUP);
    }
}
