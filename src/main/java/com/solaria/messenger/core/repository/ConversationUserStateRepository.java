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
 * Repositório do estado pessoal de conversa ({@link ConversationUserState}), implementado com {@link MongoTemplate}.
 *
 * <p>Oferece consultas por conversa e usuário, inclusive em lote, a inserção inicial da admissão, a
 * marca de saída ({@code left_at_version}) e a atualização parcial: só os campos informados são alterados, e o filtro
 * condicional evita escrita quando nada muda.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #findByConversationIdAndUserId(String, UUID)} -> estado pessoal de um participante.</li>
 *     <li>{@link #findByUserIdAndConversationIds(UUID, Set)} -> estados de um usuário em um conjunto de conversas.</li>
 *     <li>{@link #findLeftAfter(UUID, long)} -> conversas que o usuário perdeu depois de uma versão.</li>
 *     <li>{@link #insert(ConversationUserState)} -> grava o estado inicial de uma admissão.</li>
 *     <li>{@link #markLeft(String, UUID, long)} -> registra a versão em que o usuário perdeu a participação.</li>
 *     <li>{@link #clearLeft(String, UUID)} -> limpa a marca de saída na readmissão.</li>
 *     <li>{@link #patchPartial(String, UUID, Integer, Boolean, Boolean)} -> aplica apenas os campos informados.</li>
 * </ul>
 */
@Repository
public class ConversationUserStateRepository {

    private static final String CONVERSATION_ID = "conversationId";
    private static final String USER_ID = "userId";
    private static final String LEFT_AT_VERSION = "leftAtVersion";

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

    /** Estados do usuário em que a perda de participação aconteceu depois da versão informada. */
    public List<ConversationUserState> findLeftAfter(UUID userId, long since) {
        return mongoTemplate.find(Query.query(Criteria.where(USER_ID).is(userId)
                .and(LEFT_AT_VERSION).gt(since)), ConversationUserState.class);
    }

    /** Grava o estado inicial de uma admissão; dentro de transação participa do mesmo commit. */
    public ConversationUserState insert(ConversationUserState state) {
        return mongoTemplate.insert(state);
    }

    /**
     * Registra a versão em que o usuário perdeu a participação. Só grava se ainda não houver marca: repetir a remoção
     * (o DELETE da comunidade pode ser reenviado) mantém a primeira versão.
     */
    public boolean markLeft(String conversationId, UUID userId, long version) {
        Query query = Query.query(Criteria.where(CONVERSATION_ID).is(conversationId)
                .and(USER_ID).is(userId).and(LEFT_AT_VERSION).is(null));
        return mongoTemplate.updateFirst(query, new Update().set(LEFT_AT_VERSION, version),
                ConversationUserState.class).getModifiedCount() > 0;
    }

    /** Apaga os estados de uma conversa apagada. */
    public void deleteByConversationId(String conversationId) {
        mongoTemplate.remove(Query.query(Criteria.where(CONVERSATION_ID).is(conversationId)), ConversationUserState.class);
    }

    /** Limpa a marca de saída quando o usuário volta à conversa e o estado antigo é reaproveitado. */
    public boolean clearLeft(String conversationId, UUID userId) {
        Query query = Query.query(Criteria.where(CONVERSATION_ID).is(conversationId)
                .and(USER_ID).is(userId).and(LEFT_AT_VERSION).ne(null));
        return mongoTemplate.updateFirst(query, new Update().unset(LEFT_AT_VERSION),
                ConversationUserState.class).getModifiedCount() > 0;
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
