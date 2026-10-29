package com.solaria.messenger.core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.MessageType;

/**
 * Cobre contra um MongoDB real as consultas e gravações do catch-up que mocks não conseguem validar: o carimbo de
 * {@code change_version} no CAS de edição e exclusão, a consulta {@code findUpdates} com limite por conversa perdida, e
 * a marca {@code left_at_version}. Usa o mesmo Mongo que o {@code MessagerApplicationTests} já exige.
 */
@DataMongoTest
@Import(ConversationUserStateRepository.class)
class MessageUpdatesMongoTests {

    private static final String CONVERSATION_A = "upd-conv-a-" + UUID.randomUUID();
    private static final String CONVERSATION_B = "upd-conv-b-" + UUID.randomUUID();
    private static final String CONVERSATION_C = "upd-conv-c-" + UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MessageRepository messages;

    @Autowired
    private ConversationUserStateRepository states;

    @Autowired
    private MongoTemplate mongo;

    @AfterEach
    void cleanUp() {
        mongo.remove(new Query(Criteria.where("conversation_id").in(CONVERSATION_A, CONVERSATION_B, CONVERSATION_C)), "messages");
        mongo.remove(new Query(Criteria.where("user_id").is(USER)), "conversation_user_states");
    }

    @Test
    void findUpdatesReturnsReadableConversationsAndStopsLostOnesAtTheLeftVersion() {
        insert(CONVERSATION_A, 1, 5);
        insert(CONVERSATION_A, 2, 10);
        insert(CONVERSATION_A, 3, 20);
        insert(CONVERSATION_B, 1, 6);
        insert(CONVERSATION_B, 2, 11);
        insert(CONVERSATION_B, 3, 15);
        insert(CONVERSATION_C, 1, 7);

        List<Message> found = messages.findUpdates(Set.of(CONVERSATION_A), Map.of(CONVERSATION_B, 12L), 5, 10);

        // A legível entra inteira; B só até a versão 12; C não é do usuário; a versão 5 já foi vista (since é
        // exclusivo)
        assertThat(found).extracting(Message::getChangeVersion).containsExactly(6L, 10L, 11L, 20L);
        assertThat(found).extracting(Message::getConversationId)
                .containsExactly(CONVERSATION_B, CONVERSATION_A, CONVERSATION_B, CONVERSATION_A);
    }

    @Test
    void findUpdatesPagesByLimitInAscendingVersionOrder() {
        insert(CONVERSATION_A, 1, 5);
        insert(CONVERSATION_A, 2, 10);
        insert(CONVERSATION_A, 3, 20);

        assertThat(messages.findUpdates(Set.of(CONVERSATION_A), Map.of(), 0, 2))
                .extracting(Message::getChangeVersion).containsExactly(5L, 10L);
        assertThat(messages.findUpdates(Set.of(CONVERSATION_A), Map.of(), 10, 2))
                .extracting(Message::getChangeVersion).containsExactly(20L);
    }

    @Test
    void findUpdatesIgnoresLegacyMessagesWithoutChangeVersionAndEmptyScopes() {
        mongo.getCollection("messages").insertOne(new Document("conversation_id", CONVERSATION_A)
                .append("sequence", 1).append("message_type", "USER_TO_GROUP"));

        assertThat(messages.findUpdates(Set.of(CONVERSATION_A), Map.of(), 0, 10)).isEmpty();
        assertThat(messages.findUpdates(Set.of(), Map.of(), 0, 10)).isEmpty();
    }

    @Test
    void editAndDeleteStampTheChangeVersionInTheSameCas() {
        UUID sender = UUID.randomUUID();
        Message saved = messages.insert(message(CONVERSATION_A, 1, 5, sender));

        assertThat(messages.editIfCurrent(saved.getId(), CONVERSATION_A, sender, 0, "novo texto", 1, 42)).isEqualTo(1);
        Message edited = messages.findById(saved.getId()).orElseThrow();
        assertThat(edited.getContent()).isEqualTo("novo texto");
        assertThat(edited.isEdited()).isTrue();
        assertThat(edited.getChangeVersion()).isEqualTo(42);

        // época velha: o CAS não aplica e a versão não anda
        assertThat(messages.editIfCurrent(saved.getId(), CONVERSATION_A, sender, 0, "outro", 1, 43)).isZero();
        assertThat(messages.findById(saved.getId()).orElseThrow().getChangeVersion()).isEqualTo(42);

        assertThat(messages.deleteIfCurrent(saved.getId(), CONVERSATION_A, sender, 1, 2, 50)).isEqualTo(1);
        Message deleted = messages.findById(saved.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getContent()).isNull();
        assertThat(deleted.getChangeVersion()).isEqualTo(50);
    }

    @Test
    void leftMarkIsSetOnceFilteredBySinceAndClearedOnReadmission() {
        insertState(CONVERSATION_A);
        insertState(CONVERSATION_B);

        assertThat(states.markLeft(CONVERSATION_A, USER, 30)).isTrue();
        // repetir a remoção não move a primeira marca
        assertThat(states.markLeft(CONVERSATION_A, USER, 99)).isFalse();
        assertThat(states.markLeft(CONVERSATION_B, USER, 8)).isTrue();

        assertThat(states.findLeftAfter(USER, 10)).extracting(ConversationUserState::getConversationId)
                .containsExactly(CONVERSATION_A);
        assertThat(states.findLeftAfter(USER, 10).getFirst().getLeftAtVersion()).isEqualTo(30);

        assertThat(states.clearLeft(CONVERSATION_A, USER)).isTrue();
        assertThat(states.clearLeft(CONVERSATION_A, USER)).isFalse();
        assertThat(states.findLeftAfter(USER, 0)).extracting(ConversationUserState::getConversationId)
                .containsExactly(CONVERSATION_B);

        // depois de limpa, uma nova saída grava de novo
        assertThat(states.markLeft(CONVERSATION_A, USER, 60)).isTrue();
        assertThat(states.findByConversationIdAndUserId(CONVERSATION_A, USER).orElseThrow().getLeftAtVersion()).isEqualTo(60);
    }

    private void insert(String conversationId, int sequence, long changeVersion) {
        messages.insert(message(conversationId, sequence, changeVersion, UUID.randomUUID()));
    }

    private static Message message(String conversationId, int sequence, long changeVersion, UUID sender) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(sender);
        message.setRole("user");
        message.setMessageType(MessageType.USER_TO_GROUP);
        message.setContent("texto " + sequence);
        message.setTimestamp(Instant.now());
        message.setSequence(sequence);
        message.setChangeVersion(changeVersion);
        return message;
    }

    private void insertState(String conversationId) {
        ConversationUserState state = new ConversationUserState();
        state.setConversationId(conversationId);
        state.setUserId(USER);
        state.setBaselineSequence(0);
        state.setLastReadSequence(0);
        state.setMuted(false);
        state.setArchived(false);
        states.insert(state);
    }
}
