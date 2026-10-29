package com.solaria.messenger.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Confere, sem banco, os índices que o Spring Data cria a partir das anotações de {@link Message}: o índice único da
 * sequência continua único e aparece uma vez só, e o índice do catch-up por {@code change_version} existe.
 */
class MessageIndexesTests {

    @Test
    void declaresTheSequenceIndexOnceAndUniqueAndTheChangeVersionIndexOnce() {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        context.getPersistentEntity(Message.class);

        List<IndexDefinition> indexes = new ArrayList<>();
        new MongoPersistentEntityIndexResolver(context).resolveIndexFor(Message.class).forEach(indexes::add);

        assertThat(indexes).filteredOn(index -> "conversation_sequence_idx".equals(options(index).getString("name")))
                .hasSize(1)
                .allSatisfy(index -> {
                    assertThat(options(index).getBoolean("unique")).isTrue();
                    assertThat(keys(index)).isEqualTo(new Document("conversation_id", 1).append("sequence", 1));
                });
        assertThat(indexes).filteredOn(index -> "conversation_change_version_idx".equals(options(index).getString("name")))
                .hasSize(1)
                .allSatisfy(index -> {
                    assertThat(options(index).getBoolean("unique", false)).isFalse();
                    assertThat(keys(index)).isEqualTo(new Document("conversation_id", 1).append("change_version", 1));
                });
    }

    private static Document options(IndexDefinition index) {
        return index.getIndexOptions();
    }

    private static Document keys(IndexDefinition index) {
        return index.getIndexKeys();
    }
}
