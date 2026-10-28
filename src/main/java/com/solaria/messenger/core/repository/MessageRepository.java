package com.solaria.messenger.core.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bson.Document;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.repository.projection.ConversationMessageHead;
import com.solaria.messenger.core.repository.projection.LatestMessage;

/**
 * Repositório da entidade {@link Message}.
 *
 * <p>Concentra a paginação por cursor de sequência, a alocação de sequência por conversa com nova
 * tentativa sobre o índice único {@code conversation_sequence_idx}, as mutações condicionais (edição e
 * remoção lógica) e as agregações em lote de última mensagem e de mensagens não lidas.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #findByConversationIdOrderBySequenceDesc(String, Limit)} -> página das mensagens mais recentes.</li>
 *     <li>{@link #findByConversationIdAndSequenceLessThanOrderBySequenceDesc(String, int, Limit)} -> página anterior ao cursor.</li>
 *     <li>{@link #insertWithPersistedSequence(Message)} -> insere a mensagem com a próxima sequência persistida.</li>
 *     <li>{@link #editIfCurrent(String, String, UUID, long, String, long)} -> edita o conteúdo na época observada.</li>
 *     <li>{@link #deleteIfCurrent(String, String, UUID, long, long)} -> aplica a remoção lógica na época observada.</li>
 *     <li>{@link #findLatestByConversationIds(Set)} -> cabeça persistida de cada conversa do lote.</li>
 *     <li>{@link #countUnreadByConversationAndUser(Map)} -> contagem de mensagens não lidas em lote.</li>
 * </ul>
 */
public interface MessageRepository extends MongoRepository<Message, String> {

    /** Mensagem de maior sequência da conversa. */
    Optional<Message> findFirstByConversationIdOrderBySequenceDesc(String conversationId);

    /** Maior posição efetivamente persistida; zero representa histórico vazio. */
    default int persistedHeadSequence(String conversationId) {
        return findFirstByConversationIdOrderBySequenceDesc(conversationId).map(Message::getSequence).orElse(0);
    }

    /** Página das mensagens mais recentes, em ordem decrescente de sequência. */
    List<Message> findByConversationIdOrderBySequenceDesc(String conversationId, Limit limit);

    /** Página de mensagens anteriores ao cursor, em ordem decrescente de sequência. */
    List<Message> findByConversationIdAndSequenceLessThanOrderBySequenceDesc(
            String conversationId, int sequence, Limit limit);

    /** Página de mensagens posteriores ao cursor, em ordem crescente de sequência. */
    List<Message> findByConversationIdAndSequenceGreaterThanOrderBySequenceAscTimestampAsc(
            String conversationId, int sequence, Limit limit);

    /** Cada chamada insere uma nova identidade; correlação não participa da decisão de persistência. */
    default Message insertWithPersistedSequence(Message message) {
        for (int attempt = 0; attempt < 3; attempt++) {
            int maximum;
            try {
                maximum = persistedHeadSequence(message.getConversationId());
            } catch (RuntimeException readFailure) {
                throw new DataRetrievalFailureException("Não foi possível consultar a sequência persistida.", readFailure);
            }
            if (maximum == Integer.MAX_VALUE) {
                throw new OptimisticLockingFailureException("A sequência da conversa foi esgotada.");
            }
            message.setSequence(maximum + 1);
            try {
                return insert(message);
            } catch (DuplicateKeyException collision) {
                if (!isSequenceCollision(collision, message)) throw collision;
                if (attempt == 2) {
                    throw new OptimisticLockingFailureException("CONCURRENT_MODIFICATION", collision);
                }
            }
        }
        throw new IllegalStateException("Alocação de sequência esgotada.");
    }

    /** Distingue a colisão do índice de sequência desta tentativa de qualquer outra violação de unicidade. */
    private static boolean isSequenceCollision(DuplicateKeyException collision, Message attempted) {
        Pattern duplicate = Pattern.compile("index:\\s+conversation_sequence_idx\\s+dup key:\\s*\\{\\s*"
                + "conversation_id:\\s*[\"']" + Pattern.quote(attempted.getConversationId())
                + "[\"']\\s*,\\s*sequence:\\s*" + attempted.getSequence() + "\\s*\\}");
        for (Throwable cause = collision; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && duplicate.matcher(cause.getMessage()).find()) return true;
        }
        return false;
    }

    /** Updates seletivos preservam autoria, posição histórica e exclusão terminal. */
    @Query("{ '_id': ?0, 'conversation_id': ?1, 'sender_id': ?2, 'deleted': { '$ne': true }, "
            + "'$or': [ { 'mutation_epoch': ?3 }, { 'mutation_epoch': { '$exists': false }, '$expr': { '$eq': [ ?3, 0 ] } } ] }")
    @Update("{ '$set': { 'content': ?4, 'edited': true, 'mutation_epoch': ?5 } }")
    long editIfCurrent(String id, String conversationId, UUID actor, long epoch, String content, long nextEpoch);

    /** Aplica o tombstone somente se autoria e época de mutação ainda forem as observadas. */
    @Query("{ '_id': ?0, 'conversation_id': ?1, 'sender_id': ?2, 'deleted': { '$ne': true }, "
            + "'$or': [ { 'mutation_epoch': ?3 }, { 'mutation_epoch': { '$exists': false }, '$expr': { '$eq': [ ?3, 0 ] } } ] }")
    @Update("{ '$set': { 'deleted': true, 'mutation_epoch': ?4 }, '$unset': { 'content': '' } }")
    long deleteIfCurrent(String id, String conversationId, UUID actor, long epoch, long nextEpoch);

    /** Cabeça persistida e última mensagem de cada conversa; conversa sem mensagens fica com sequência zero. */
    default Map<String, ConversationMessageHead> findLatestByConversationIds(Set<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, ConversationMessageHead> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, new ConversationMessageHead(0, null)));
        for (Message message : aggregateLatestMessages(ids)) {
            if (result.containsKey(message.getConversationId())) {
                LatestMessage latest = new LatestMessage(message.getId(), message.getSequence(),
                        message.getSenderId(), message.getContent(), message.isEdited(), message.isDeleted(),
                        message.getTimestamp());
                result.put(message.getConversationId(), new ConversationMessageHead(message.getSequence(), latest));
            }
        }
        return result;
    }

    /** A consulta REST e a projeção privada usam a mesma regra de unread em lote. */
    default Map<String, Long> countUnreadByConversationCursors(UUID actor,
            Map<String, Integer> cursorsByConversation) {
        Objects.requireNonNull(actor, "actor");
        Map<String, Map<UUID, Integer>> windows = new LinkedHashMap<>();
        cursorsByConversation.forEach((id, cursor) -> windows.put(id, Map.of(actor, cursor)));
        Map<String, Map<UUID, Long>> counts = countUnreadByConversationAndUser(windows);
        Map<String, Long> result = new LinkedHashMap<>();
        cursorsByConversation.keySet().forEach(id -> result.put(id, counts.get(id).get(actor)));
        return result;
    }

    /** Uma agregação atende várias conversas e destinatários, sem consultar a lista por usuário. */
    default Map<String, Map<UUID, Long>> countUnreadByConversationAndUser(
            Map<String, Map<UUID, Integer>> cursors) {
        if (cursors.isEmpty()) return Map.of();
        Map<String, Map<UUID, Long>> result = new LinkedHashMap<>();
        List<Document> windows = new ArrayList<>();
        List<Document> ranges = new ArrayList<>();
        cursors.forEach((id, users) -> {
            Map<UUID, Long> counts = new LinkedHashMap<>();
            users.forEach((actor, cursor) -> {
                counts.put(actor, 0L);
                windows.add(new Document("resourceKey", id).append("actor", actor)
                        .append("userKey", actor.toString()).append("cursor", cursor));
            });
            result.put(id, counts);
            if (!users.isEmpty()) ranges.add(new Document("conversation_id", id).append("sequence",
                    new Document("$gt", users.values().stream().min(Integer::compareTo).orElseThrow())));
        });
        if (ranges.isEmpty()) return result;
        Document match = new Document("$or", ranges).append("deleted", new Document("$ne", true));
        Document eligible = new Document("$filter", new Document("input", new Document("$literal", windows))
                .append("as", "window").append("cond", new Document("$and", List.of(
                        new Document("$eq", List.of("$conversation_id", "$$window.resourceKey")),
                        new Document("$ne", List.of(new Document("$ifNull", List.of("$sender_id", "")), "$$window.actor")),
                        new Document("$gt", List.of("$sequence", "$$window.cursor"))))));
        for (Document row : aggregateUnreadWindows(match, new Document("eligible", eligible)).getMappedResults()) {
            Document key = row.get("_id", Document.class);
            String id = key.getString("resourceKey");
            UUID actor = UUID.fromString(key.getString("userKey"));
            if (result.containsKey(id) && result.get(id).containsKey(actor)) {
                result.get(id).put(actor, ((Number) row.get("count")).longValue());
            }
        }
        return result;
    }

    // O wrapper preserva o documento agrupado; List<Document> é tratado como resultado escalar pelo Spring Data.
    @Aggregation(pipeline = {
            "{ '$match': ?0 }", "{ '$project': ?1 }", "{ '$unwind': '$eligible' }",
            "{ '$group': { '_id': { 'resourceKey': '$eligible.resourceKey', 'userKey': '$eligible.userKey' }, 'count': { '$sum': 1 } } }"
    })
    AggregationResults<Document> aggregateUnreadWindows(Document match, Document projection);

    /** Última mensagem de cada conversa do lote, numa única agregação. */
    @Aggregation(pipeline = {
            "{ '$match': { 'conversation_id': { '$in': ?0 } } }",
            "{ '$sort': { 'conversation_id': -1, 'sequence': -1 } }",
            "{ '$group': { '_id': '$conversation_id', 'latest': { '$first': '$$ROOT' } } }",
            "{ '$replaceRoot': { 'newRoot': '$latest' } }"
    })
    List<Message> aggregateLatestMessages(Set<String> ids);
}
