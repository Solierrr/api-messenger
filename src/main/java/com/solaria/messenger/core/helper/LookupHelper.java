package com.solaria.messenger.core.helper;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.RatingRepository;

/**
 * Busca conversas, comunidades e avaliações pelo identificador.
 *
 * <p>
 * Centraliza a leitura pelo id e a mensagem de erro de cada tipo de recurso. Quando o id não existe, lança
 * {@link ResourceNotFoundException}. Os métodos não alteram nada.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #requireConversation(String)} -> carrega a conversa ou lança erro.</li>
 *     <li>{@link #requireParentCommunity(String)} -> carrega a comunidade pai de um subgrupo.</li>
 *     <li>{@link #requireCommunity(String)} -> carrega a comunidade pelo id.</li>
 *     <li>{@link #requireRating(String)} -> carrega a avaliação pelo id.</li>
 * </ul>
 */
@Component
public class LookupHelper {

    private final ConversationRepository conversations;
    private final CommunityRepository communities;
    private final RatingRepository ratings;

    public LookupHelper(ConversationRepository conversations, CommunityRepository communities, RatingRepository ratings) {
        this.conversations = conversations;
        this.communities = communities;
        this.ratings = ratings;
    }

    /**
     * Carrega a conversa pelo identificador.
     *
     * @param id identificador da conversa.
     * @return conversa encontrada.
     * @throws ResourceNotFoundException caso a conversa não exista.
     */
    public Conversation requireConversation(String id) {
        return conversations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada com id: " + id));
    }

    // mesma busca de requireCommunity, com o texto de erro que os fluxos de conversa sempre usaram
    /**
     * Carrega a comunidade pai de um subgrupo.
     *
     * <p>
     * Usa a mesma busca de {@link #requireCommunity(String)}, mas com o texto de erro que os fluxos de conversa
     * sempre devolveram.
     * </p>
     *
     * @param id identificador da comunidade.
     * @return comunidade encontrada.
     * @throws ResourceNotFoundException caso a comunidade não exista.
     */
    public ProjectCommunity requireParentCommunity(String id) {
        return communities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada: " + id));
    }

    /**
     * Carrega a comunidade pelo identificador.
     *
     * @param id identificador da comunidade.
     * @return comunidade encontrada.
     * @throws ResourceNotFoundException caso a comunidade não exista.
     */
    public ProjectCommunity requireCommunity(String id) {
        return communities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada com id: " + id));
    }

    /**
     * Carrega a avaliação pelo identificador.
     *
     * @param id identificador da avaliação.
     * @return avaliação encontrada.
     * @throws ResourceNotFoundException caso a avaliação não exista.
     */
    public Rating requireRating(String id) {
        return ratings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada com ID: " + id));
    }
}
