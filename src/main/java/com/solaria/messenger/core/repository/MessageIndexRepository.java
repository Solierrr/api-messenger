package com.solaria.messenger.core.repository;

import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.stereotype.Repository;

import com.solaria.messenger.core.model.Message;

import jakarta.annotation.PostConstruct;

/**
 * Remove o índice de deduplicação {@code sender_client_message_id_idx}, se existir; dados e unicidade de sequência
 * permanecem.
 */
@Repository
public class MessageIndexRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageIndexRepository.class);
    private static final String LEGACY_INDEX = "sender_client_message_id_idx";
    private static final Set<String> CORRELATION_FIELDS = Set.of("sender_id", "client_message_id");
    private final MongoTemplate mongo;

    public MessageIndexRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * O índice pode existir no banco sem anotação no modelo; uma definição inesperada impede iniciar com dedup ativa
     */
    @PostConstruct
    public void removeLegacyDeduplicationIndex() {
        if (!mongo.collectionExists(Message.class)) return;
        var indexes = mongo.indexOps(Message.class);
        var definitions = indexes.getIndexInfo();
        for (var index : definitions) {
            boolean correlation = index.getIndexFields().size() == 2 && index.getIndexFields().stream()
                    .map(IndexField::getKey).collect(Collectors.toSet()).equals(CORRELATION_FIELDS);
            if (LEGACY_INDEX.equals(index.getName()) && !correlation) {
                throw new IllegalStateException("O índice legado de mensagens tem uma definição inesperada.");
            }
            if (correlation && index.isUnique() && !LEGACY_INDEX.equals(index.getName())) {
                throw new IllegalStateException("Outro índice único de correlação impede o contrato sem deduplicação: "
                        + index.getName());
            }
        }
        if (definitions.stream().anyMatch(index -> LEGACY_INDEX.equals(index.getName()))) {
            indexes.dropIndex(LEGACY_INDEX);
            log.info("Índice legado de deduplicação removido da coleção de mensagens");
        }
    }
}
