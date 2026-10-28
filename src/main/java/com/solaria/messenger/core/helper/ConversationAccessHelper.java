package com.solaria.messenger.core.helper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;

/**
 * Confere a participação do user nas conversas e nas comunidades.
 *
 * <p>
 * É o ponto único de autorização de leitura e escrita nas conversas. Em subgrupo, a participação vale só se o user
 * também for membro atual da comunidade. Ao carregar conversas em lote, reaproveita as comunidades já carregadas.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> carrega as conversas.</li>
 *     <li>{@link CommunityRepository} -> carrega as comunidades dos subgrupos.</li>
 *     <li>{@link LookupHelper} -> recarrega a conversa e a comunidade pai.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #requireParticipant(String, UUID)} -> carrega a conversa e exige participação.</li>
 *     <li>{@link #requireParticipant(Conversation, UUID)} -> exige participação a partir de uma conversa já carregada.</li>
 *     <li>{@link #withAccess(String, UUID, Function)} -> executa uma ação com a conversa já autorizada.</li>
 *     <li>{@link #requireCommunityActive(Conversation)} -> exige comunidade ativa nos subgrupos.</li>
 *     <li>{@link #communitiesFor(List)} -> carrega as comunidades de um lote de conversas.</li>
 *     <li>{@link #authorizedParticipants(List)} -> devolve quem pode ver cada conversa do lote.</li>
 *     <li>{@link #authorizedParticipants(Set)} -> recarrega as conversas pelos ids e refaz a autorização.</li>
 * </ul>
 */
@Component
public class ConversationAccessHelper {

    private final ConversationRepository conversations;
    private final CommunityRepository communities;
    private final LookupHelper lookup;

    public ConversationAccessHelper(ConversationRepository conversations, CommunityRepository communities, LookupHelper lookup) {
        this.conversations = conversations;
        this.communities = communities;
        this.lookup = lookup;
    }

    /**
     * Carrega a conversa e exige que o user participe dela.
     *
     * <p>
     * Para subgrupo, o user também precisa ser membro atual da comunidade. Isso vale até no meio da remoção de um
     * membro da comunidade.
     * </p>
     *
     * @param conversationId identificador da conversa.
     * @param actor identificador do user.
     * @return conversa carregada.
     * @throws ResourceNotFoundException caso a conversa ou a comunidade do subgrupo não exista.
     * @throws UnauthorizedAccessException caso o user não participe da conversa ou da comunidade.
     */
    public Conversation requireParticipant(String conversationId, UUID actor) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada: " + conversationId));
        if (!conversation.hasParticipant(actor)) {
            throw new UnauthorizedAccessException("O usuário não participa desta conversa.");
        }

        // vale também no meio de uma remoção da comunidade: fora dela, sem acesso ao subgrupo
        if (conversation.getCommunityId() != null) {
            ProjectCommunity community = communities.findById(conversation.getCommunityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada."));
            if (!community.isMember(actor)) {
                throw new UnauthorizedAccessException("O usuário não participa desta comunidade.");
            }
        }
        return conversation;
    }

    /**
     * Confere a participação de quem já tem a conversa em mãos.
     *
     * <p>
     * Relê a conversa pelo id, para usar a participação atual. Se o objeto ainda não tem id, usa o próprio
     * objeto.
     * </p>
     *
     * @param conversation conversa já carregada.
     * @param actor identificador do user.
     * @throws UnauthorizedAccessException caso o user não participe da conversa ou da comunidade pai.
     */
    public void requireParticipant(Conversation conversation, UUID actor) {
        Conversation latest = conversation.getId() == null ? conversation : lookup.requireConversation(conversation.getId());
        if (!latest.hasParticipant(actor)) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
        ProjectCommunity community = latest.getCommunityId() == null ? null : lookup.requireParentCommunity(latest.getCommunityId());
        if (community != null && !community.isMember(actor)) {
            throw new UnauthorizedAccessException("O usuário não participa desta comunidade.");
        }
    }

    /**
     * Devolve, para cada conversa do lote, quem pode vê-la.
     *
     * <p>
     * Carrega as comunidades do lote uma vez só e reaproveita na autorização.
     * </p>
     *
     * @param current conversas do lote.
     * @return mapa de id da conversa para os participantes autorizados.
     */
    public Map<String, Set<UUID>> authorizedParticipants(List<Conversation> current) {
        return authorizedParticipants(current, communitiesFor(current));
    }

    /**
     * Carrega as comunidades das conversas do lote, de uma vez só.
     *
     * <p>
     * As comunidades carregadas servem à autorização e à projeção, para não buscar duas vezes.
     * </p>
     *
     * @param current conversas do lote.
     * @return mapa de id da comunidade para a comunidade carregada.
     */
    public Map<String, ProjectCommunity> communitiesFor(List<Conversation> current) {
        Set<String> parentIds = current.stream().map(Conversation::getCommunityId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, ProjectCommunity> parents = new LinkedHashMap<>();
        if (!parentIds.isEmpty()) communities.findAllById(parentIds).forEach(parent -> parents.put(parent.getId(), parent));
        return parents;
    }

    /**
     * Mantém, em cada conversa, só os participantes que ainda são membros da comunidade.
     *
     * <p>
     * Conversas fora de comunidade mantêm todos os participantes.
     * </p>
     *
     * @param current conversas do lote.
     * @param parents comunidades do lote, por id.
     * @return mapa de id da conversa para os participantes autorizados.
     */
    public Map<String, Set<UUID>> authorizedParticipants(List<Conversation> current, Map<String, ProjectCommunity> parents) {
        Map<String, Set<UUID>> result = new LinkedHashMap<>();
        for (Conversation conversation : current) {
            ProjectCommunity parent = parents.get(conversation.getCommunityId());
            Set<UUID> users = conversation.getParticipantIds().stream()
                    .filter(user -> conversation.getCommunityId() == null || parent != null && parent.isMember(user))
                    .collect(Collectors.toSet());
            result.put(conversation.getId(), Set.copyOf(users));
        }
        return result;
    }

    /**
     * Recarrega as conversas pelos ids e refaz a autorização.
     *
     * <p>
     * Usado logo antes de publicar, para não entregar a quem perdeu acesso no meio do caminho.
     * </p>
     *
     * @param ids identificadores das conversas.
     * @return mapa de id da conversa para os participantes autorizados.
     */
    public Map<String, Set<UUID>> authorizedParticipants(Set<String> ids) {
        return authorizedParticipants(conversations.findAllById(ids));
    }

    /**
     * Autoriza o user e executa a ação com a conversa já carregada.
     *
     * @param <T> tipo do resultado da ação.
     * @param conversationId identificador da conversa.
     * @param actor identificador do user.
     * @param action ação a executar com a conversa autorizada.
     * @return resultado da ação.
     * @throws UnauthorizedAccessException caso o user não participe da conversa ou da comunidade.
     */
    public <T> T withAccess(String conversationId, UUID actor, Function<Conversation, T> action) {
        return action.apply(requireParticipant(conversationId, actor));
    }

    /**
     * Confere que a comunidade do subgrupo está ativa.
     *
     * <p>
     * Comunidade arquivada bloqueia mensagens e a administração dos subgrupos. Conversa fora de comunidade passa sem
     * checagem.
     * </p>
     *
     * @param conversation conversa a checar.
     * @throws ResourceNotFoundException caso a comunidade da conversa não exista.
     * @throws BusinessRuleException caso a comunidade esteja arquivada.
     */
    public void requireCommunityActive(Conversation conversation) {
        if (conversation.getCommunityId() == null) return;

        ProjectCommunity community = communities.findById(conversation.getCommunityId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comunidade não encontrada para a conversa: " + conversation.getId()));
        PureRulesHelper.requireCommunityActive(community);
    }
}
