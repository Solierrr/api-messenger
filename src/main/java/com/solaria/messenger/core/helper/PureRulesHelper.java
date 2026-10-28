package com.solaria.messenger.core.helper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;

/**
 * Funções puras de regra de negócio, usadas pelos use cases sem acesso a banco nem a outros componentes.
 *
 * <p>
 * Reúne as regras que só recebem dados e devolvem resultado ou erro: papéis e permissões na comunidade, estado da
 * conversa, versão de CAS e montagem de objetos novos. Como não há I/O nem estado, todos os métodos são estáticos. A
 * classe não pode ser instanciada.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #applyOptimistic} -> aplica uma escrita com CAS e relê entre as tentativas.</li>
 *     <li>{@link #nextVersion} -> calcula a próxima versão do documento.</li>
 *     <li>{@link #requireMember} -> exige que o ator seja membro.</li>
 *     <li>{@link #requireAdmin} -> exige papel OWNER ou ADMIN.</li>
 *     <li>{@link #requireOwner} -> exige papel OWNER.</li>
 *     <li>{@link #requireRemovalAuthority} -> confere se o ator pode remover o alvo.</li>
 *     <li>{@link #requireCommunityActive} -> exige comunidade ativa.</li>
 *     <li>{@link #copyMembers} -> copia os membros para montar a escrita.</li>
 *     <li>{@link #requireConversationActive} -> exige conversa ativa.</li>
 *     <li>{@link #newConversation} -> monta uma conversa ativa nova.</li>
 *     <li>{@link #directKey} -> gera a chave canônica de uma direta.</li>
 *     <li>{@link #requireNoNullParticipants} -> exige participantes sem nulos.</li>
 *     <li>{@link #withoutUser} -> devolve os participantes sem um user.</li>
 * </ul>
 */
public final class PureRulesHelper {

    private static final int MAX_ATTEMPTS = 3;

    private PureRulesHelper() { }

    // CAS

    /**
     * Aplica uma escrita com CAS, relendo o documento entre as tentativas.
     *
     * <p>
     * Quando {@code mutation} devolve {@code null}, o documento atual volta sem releitura, porque não há nada a
     * gravar. Nos outros casos o documento é relido. Se o retorno for {@code 1}, a função devolve a versão relida. Caso
     * contrário, tenta de novo. Depois de três tentativas sem gravar, lança a exceção fornecida por
     * {@code exhausted}.
     * </p>
     *
     * @param <T> tipo do documento tratado pela escrita.
     * @param initial documento lido antes da primeira tentativa.
     * @param mutation escrita a aplicar sobre o documento atual.
     * @param reload relê o documento depois de cada tentativa.
     * @param exhausted fornece a exceção quando as tentativas acabam.
     * @return documento na versão mais recente, depois da escrita.
     * @throws RuntimeException o que {@code exhausted} fornecer, quando as três tentativas não gravam.
     */
    public static <T> T applyOptimistic(T initial, Function<T, Long> mutation, Supplier<T> reload,
            Supplier<? extends RuntimeException> exhausted) {
        T current = initial;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long modified = mutation.apply(current);
            if (modified == null) return current;

            // relê nos dois casos: gravou (devolve o novo estado) ou perdeu para outra escrita (tenta de novo)
            current = reload.get();
            if (modified == 1) return current;
        }
        throw exhausted.get();
    }

    /**
     * Calcula a próxima versão do documento.
     *
     * @param version versão atual. Nulo indica documento antigo, sem versão.
     * @return próxima versão, ou zero quando o documento ainda não tinha versão.
     */
    public static Long nextVersion(Long version) {
        return version == null ? 0L : Math.addExact(version, 1L);
    }

    // comunidade

    /**
     * Confere que o ator é membro da comunidade.
     *
     * @param community comunidade consultada.
     * @param actor identificador do ator.
     * @throws UnauthorizedAccessException caso o ator não seja membro.
     */
    public static void requireMember(ProjectCommunity community, UUID actor) {
        if (!community.isMember(actor)) {
            throw new UnauthorizedAccessException("O usuário autenticado não participa desta comunidade.");
        }
    }

    /**
     * Confere que o ator tem papel OWNER ou ADMIN na comunidade.
     *
     * @param community comunidade consultada.
     * @param actor identificador do ator.
     * @throws UnauthorizedAccessException caso o ator não tenha papel OWNER ou ADMIN.
     */
    public static void requireAdmin(ProjectCommunity community, UUID actor) {
        if (!community.isAdmin(actor)) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER ou ADMIN na comunidade.");
        }
    }

    /**
     * Confere que o ator é o OWNER da comunidade.
     *
     * @param community comunidade consultada.
     * @param actor identificador do ator.
     * @throws UnauthorizedAccessException caso o ator não seja OWNER.
     */
    public static void requireOwner(ProjectCommunity community, UUID actor) {
        if (!community.isOwner(actor)) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER na comunidade.");
        }
    }

    /**
     * Confere se o ator pode remover o alvo da comunidade.
     *
     * <p>
     * Ninguém remove o {@code OWNER}. Remover outra pessoa exige ser OWNER ou ADMIN. Sair por conta própria não
     * exige papel. Na remoção de terceiros, a comunidade precisa estar ativa enquanto o alvo ainda for membro.
     * </p>
     *
     * @param community comunidade atual.
     * @param actor identificador de quem pede a remoção.
     * @param target identificador do membro a remover.
     * @throws BusinessRuleException caso o alvo seja o OWNER, ou a comunidade esteja arquivada na remoção de terceiros.
     * @throws UnauthorizedAccessException caso o ator remova outra pessoa sem ser OWNER ou ADMIN.
     */
    public static void requireRemovalAuthority(ProjectCommunity community, UUID actor, UUID target) {
        if (community.isOwner(target)) {
            throw new BusinessRuleException("O dono da comunidade não pode ser removido.");
        }
        if (!actor.equals(target) && (!community.isMember(actor) || !community.isAdmin(actor))) {
            throw new UnauthorizedAccessException("A remoção de terceiros exige OWNER ou ADMIN atuais.");
        }
        if (community.isMember(target) && !actor.equals(target)) requireCommunityActive(community);
    }

    /**
     * Confere que a comunidade está ativa.
     *
     * @param community comunidade consultada.
     * @throws BusinessRuleException caso a comunidade esteja arquivada.
     */
    public static void requireCommunityActive(ProjectCommunity community) {
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }
    }

    /**
     * Copia a lista de membros para montar a escrita sem alterar o documento lido.
     *
     * @param members membros atuais. Nulo vira lista vazia.
     * @return cópia independente da lista, com os mesmos papéis e datas de entrada.
     */
    public static List<CommunityMember> copyMembers(List<CommunityMember> members) {
        return members == null ? new ArrayList<>() : members.stream()
                .map(member -> CommunityMember.of(member.getUserId(), member.getRole(), member.getJoinedAt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // conversa

    /**
     * Confere que a conversa está ativa para receber comandos.
     *
     * <p>
     * Conversa desativada não aceita comandos, mas continua podendo ser lida.
     * </p>
     *
     * @param conversation conversa consultada.
     * @throws BusinessRuleException caso a conversa esteja desativada.
     */
    public static void requireConversationActive(Conversation conversation) {
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleException("A conversa está desativada e não aceita novas mensagens.");
        }
    }

    /**
     * Monta uma conversa ativa com id próprio.
     *
     * <p>
     * Em grupo, o criador começa como gestor. A última interação começa no mesmo instante do início.
     * </p>
     *
     * @param type tipo da conversa.
     * @param createdBy criador da conversa.
     * @param participants participantes iniciais.
     * @return conversa nova, ainda não gravada.
     */
    public static Conversation newConversation(ConversationType type, UUID createdBy, Set<UUID> participants) {
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

    /**
     * Gera a chave de uma direta, que não depende da ordem dos ids.
     *
     * <p>
     * Os ids são ordenados como texto e unidos por {@code |}.
     * </p>
     *
     * @param users participantes do par.
     * @return chave canônica do par.
     */
    public static String directKey(Collection<UUID> users) {
        return String.join("|", users.stream().map(UUID::toString).sorted().toList());
    }

    /**
     * Confere que a lista de participantes não tem nulos.
     *
     * @param participants participantes a validar.
     * @throws InvalidFieldException caso algum participante seja nulo.
     */
    public static void requireNoNullParticipants(Set<UUID> participants) {
        if (participants.contains(null)) throw new InvalidFieldException("Participantes não podem ser nulos.");
    }

    /**
     * Devolve uma cópia dos participantes sem o user informado.
     *
     * @param participants participantes atuais.
     * @param user user a remover.
     * @return novo conjunto, sem alterar o original.
     */
    public static Set<UUID> withoutUser(Set<UUID> participants, UUID user) {
        Set<UUID> survivors = new LinkedHashSet<>(participants);
        survivors.remove(user);
        return survivors;
    }
}
