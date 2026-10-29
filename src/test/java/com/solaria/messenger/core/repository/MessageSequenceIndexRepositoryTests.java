package com.solaria.messenger.core.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ListCollectionNamesIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageSequenceIndexRepositoryTests {

    @Mock
    private MongoDatabaseFactory databaseFactory;
    @Mock
    private MongoDatabase database;
    @Mock
    private MongoCollection<Document> messages;
    @Mock
    private ListCollectionNamesIterable collectionNames;
    @Mock
    private ListIndexesIterable<Document> indexes;
    @Mock
    private AggregateIterable<Document> duplicates;

    private MessageSequenceIndexRepository repository;

    @BeforeEach
    void setUp() {
        given(databaseFactory.getMongoDatabase()).willReturn(database);
        given(database.listCollectionNames()).willReturn(collectionNames);
        given(database.getCollection("messages")).willReturn(messages);
        given(messages.listIndexes()).willReturn(indexes);
        given(messages.aggregate(anyList())).willReturn(duplicates);
        givenCollections("messages");
        repository = new MessageSequenceIndexRepository(databaseFactory);
    }

    @Test
    void converteIndiceLegadoSemUnicidadeEmUnico() {
        givenIndexes(sequenceIndex(false, new Document("conversation_id", 1).append("sequence", 1)));

        repository.makeSequenceIndexUnique();

        verify(messages).dropIndex("conversation_sequence_idx");
        verify(messages).createIndex(eq(new Document("conversation_id", 1).append("sequence", 1)),
                argThat((IndexOptions options) -> options.isUnique()
                        && "conversation_sequence_idx".equals(options.getName())));
    }

    @Test
    void aceitaChavesGravadasComoDouble() {
        givenIndexes(sequenceIndex(false, new Document("conversation_id", 1.0).append("sequence", 1.0)));

        repository.makeSequenceIndexUnique();

        verify(messages).dropIndex("conversation_sequence_idx");
    }

    @Test
    void naoMexeQuandoIndiceJaEUnico() {
        givenIndexes(sequenceIndex(true, new Document("conversation_id", 1).append("sequence", 1)));

        repository.makeSequenceIndexUnique();

        verify(messages, never()).dropIndex(any(String.class));
    }

    @Test
    void naoMexeQuandoColecaoNaoExiste() {
        givenCollections("conversations");

        repository.makeSequenceIndexUnique();

        verify(database, never()).getCollection("messages");
    }

    @Test
    void naoMexeQuandoIndiceNaoExiste() {
        givenIndexes(new Document("name", "_id_").append("key", new Document("_id", 1)));

        repository.makeSequenceIndexUnique();

        verify(messages, never()).dropIndex(any(String.class));
    }

    @Test
    void falhaSemRemoverQuandoHaSequenciaRepetida() {
        givenIndexes(sequenceIndex(false, new Document("conversation_id", 1).append("sequence", 1)));
        given(duplicates.first()).willReturn(new Document("_id",
                new Document("conversation_id", "c1").append("sequence", 3)).append("count", 2));

        assertThatThrownBy(repository::makeSequenceIndexUnique)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("c1");
        verify(messages, never()).dropIndex(any(String.class));
    }

    @Test
    void falhaSemRemoverQuandoDefinicaoInesperada() {
        givenIndexes(sequenceIndex(false, new Document("sequence", 1).append("conversation_id", 1)));

        assertThatThrownBy(repository::makeSequenceIndexUnique).isInstanceOf(IllegalStateException.class);
        verify(messages, never()).dropIndex(any(String.class));
    }

    private void givenCollections(String... names) {
        willAnswer(call -> {
            Collection<String> target = call.getArgument(0);
            target.addAll(List.of(names));
            return target;
        }).given(collectionNames).into(any());
    }

    private void givenIndexes(Document... definitions) {
        willAnswer(call -> {
            Collection<Document> target = call.getArgument(0);
            target.addAll(List.of(definitions));
            return target;
        }).given(indexes).into(any());
    }

    private static Document sequenceIndex(boolean unique, Document key) {
        Document index = new Document("name", "conversation_sequence_idx").append("key", key);
        return unique ? index.append("unique", true) : index;
    }
}
