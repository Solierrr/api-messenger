package com.solaria.messenger.core.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import com.solaria.messenger.core.model.ConversationUserState;

/**
 * Repositório do estado pessoal de conversa ({@link ConversationUserState}), implementado com
 * {@link MongoTemplate}.
 *
 * <p>Oferece consultas por conversa e usuário, inclusive em lote, a inserção inicial da admissão e a
 * atualização parcial: só os campos informados são alterados, e o filtro condicional evita escrita
 * quando nada muda.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #findByConversationIdAndUserId(String, UUID)} -> estado pessoal de um participante.</li>
 *     <li>{@link #findByUserIdAndConversationIds(UUID, Set)} -> estados de um usuário em um conjunto de conversas.</li>
 *     <li>{@link #insert(ConversationUserState)} -> grava o estado inicial de uma admissão.</li>
 *     <li>{@link #patchPartial(String, UUID, Integer, Boolean, Boolean)} -> aplica apenas os campos informados.</li>
 * </ul>
 */
@Repository
public class ConversationUserStateRepository {

    private static final String CONVERSATION_ID = "conversationId";
    private static final String USER_ID = "userId";

    private final MongoTemplate mongoTemplate;

    public ConversationUserStateRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Estado pessoal de um participante numa conversa. */
    public Optional<ConversationUserState> findByConversationIdAndUserId(String conversationId, UUID userId) {
        Query query = Query.query(Criteria.where(CONVERSATION_ID).is(conversationId)
                .and(USER_ID).is(userId));
        return Optional.ofNullable(mongoTemplate.findOne(query, ConversationUserState.class));
    }

    /** Estados pessoais de um usuário num conjunto de conversas. */
    public List<ConversationUserState> findByUserIdAndConversationIds(UUID userId, Set<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where(USER_ID).is(userId)
                .and(CONVERSATION_ID).in(conversationIds));
        return mongoTemplate.find(query, ConversationUserState.class);
    }

    /** Carrega os estados de um lote de projeções sem uma consulta por destinatário. */
    public List<ConversationUserState> findByUsersAndConversations(Set<UUID> users, Set<String> conversations) {
        if (users.isEmpty() || conversations.isEmpty()) return List.of();
        return mongoTemplate.find(Query.query(Criteria.where(USER_ID).in(users)
                .and(CONVERSATION_ID).in(conversations)), ConversationUserState.class);
    }

    /** Grava o estado inicial de uma admissão; dentro de transação participa do mesmo commit. */
    public ConversationUserState insert(ConversationUserState state) {
        return mongoTemplate.insert(state);
    }

    /** Aplica só os campos informados ({@code null} = não alterar), com filtro condicional que evita no-op. */
    public boolean patchPartial(String conversationId, UUID userId,
            Integer lastReadSequence, Boolean muted, Boolean archived) {
        List<Criteria> changedFields = new ArrayList<>(3);
        if (lastReadSequence != null) {
            changedFields.add(Criteria.where("lastReadSequence").lt(lastReadSequence));
        }
        if (muted != null) {
            changedFields.add(Criteria.where("muted").ne(muted));
        }
        if (archived != null) {
            changedFields.add(Criteria.where("archived").ne(archived));
        }
        if (changedFields.isEmpty()) {
            return false;
        }

        Criteria identity = new Criteria().andOperator(
                Criteria.where(CONVERSATION_ID).is(conversationId),
                Criteria.where(USER_ID).is(userId));
        Criteria hasChangedField = new Criteria().orOperator(changedFields.toArray(Criteria[]::new));
        Query query = Query.query(new Criteria().andOperator(identity, hasChangedField));
        Update update = new Update();
        if (lastReadSequence != null) {
            update.max("lastReadSequence", lastReadSequence);
        }
        if (muted != null) {
            update.set("muted", muted);
        }
        if (archived != null) {
            update.set("archived", archived);
        }
        return mongoTemplate.updateFirst(query, update, ConversationUserState.class).getModifiedCount() > 0;
    }
}
