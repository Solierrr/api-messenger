package com.solaria.messenger.core.repository;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;

import jakarta.annotation.PostConstruct;

@Repository
public class MessageSequenceIndexRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageSequenceIndexRepository.class);
    private static final String COLLECTION = "messages";
    private static final String INDEX = "conversation_sequence_idx";
    private static final Document KEYS = new Document("conversation_id", 1).append("sequence", 1);
    private final MongoDatabaseFactory databaseFactory;

    public MessageSequenceIndexRepository(MongoDatabaseFactory databaseFactory) {
        this.databaseFactory = databaseFactory;
    }

    @PostConstruct
    public void makeSequenceIndexUnique() {
        var database = databaseFactory.getMongoDatabase();
        if (!database.listCollectionNames().into(new ArrayList<>()).contains(COLLECTION)) return;
        MongoCollection<Document> messages = database.getCollection(COLLECTION);
        var legacy = messages.listIndexes().into(new ArrayList<>()).stream()
                .filter(index -> INDEX.equals(index.getString("name"))).findFirst();
        if (legacy.isEmpty() || legacy.get().getBoolean("unique", false)) return;
        if (!hasSequenceKeys(legacy.get().get("key", Document.class))) {
            throw new IllegalStateException("O índice legado de sequência tem uma definição inesperada.");
        }
        var duplicate = messages.aggregate(List.of(
                new Document("$group", new Document("_id", new Document("conversation_id", "$conversation_id")
                        .append("sequence", "$sequence")).append("count", new Document("$sum", 1))),
                new Document("$match", new Document("count", new Document("$gt", 1))),
                new Document("$limit", 1))).first();
        if (duplicate != null) {
            throw new IllegalStateException("Mensagens com sequência repetida impedem o índice único: "
                    + duplicate.get("_id", Document.class).toJson());
        }
        messages.dropIndex(INDEX);
        messages.createIndex(KEYS, new IndexOptions().name(INDEX).unique(true));
        log.info("Índice de sequência das mensagens convertido para único");
    }

    private static boolean hasSequenceKeys(Document key) {
        return key != null && List.copyOf(key.keySet()).equals(List.copyOf(KEYS.keySet()))
                && key.values().stream().allMatch(value -> value instanceof Number n && n.doubleValue() == 1);
    }
}
