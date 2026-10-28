package com.solaria.messenger.core.helper;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.RatingRepository;

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

    public Conversation requireConversation(String id) {
        return conversations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada com id: " + id));
    }

    // mesma busca de requireCommunity, com o texto de erro que os fluxos de conversa sempre usaram
    public ProjectCommunity requireParentCommunity(String id) {
        return communities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada: " + id));
    }

    public ProjectCommunity requireCommunity(String id) {
        return communities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada com id: " + id));
    }

    /** Carrega a avaliação ou lança recurso inexistente. */
    public Rating requireRating(String id) {
        return ratings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada com ID: " + id));
    }
}
