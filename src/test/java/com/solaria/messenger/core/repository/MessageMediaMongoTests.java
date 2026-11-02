package com.solaria.messenger.core.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.model.enums.MessageType;

/**
 * Cobre contra um MongoDB real a lista de mídias ainda disponíveis (só arquivo vivo, mais recente primeiro, com cursor
 * por versão) e a exclusão, que precisa tirar o anexo do documento. Usa o mesmo Mongo do {@code MessageUpdatesMongoTests}.
 */
@DataMongoTest
class MessageMediaMongoTests {

    private static final String CONVERSATION_A = "media-conv-a-" + UUID.randomUUID();
    private static final String CONVERSATION_B = "media-conv-b-" + UUID.randomUUID();
    private static final String OTHER = "media-conv-other-" + UUID.randomUUID();

    @Autowired
    private MessageRepository messages;

    @Autowired
    private MongoTemplate mongo;

    @AfterEach
    void cleanUp() {
        mongo.remove(new Query(Criteria.where("conversation_id").in(CONVERSATION_A, CONVERSATION_B, OTHER)), "messages");
    }

    @Test
    void listsOnlyLiveFilesNewestVersionFirst() {
        insert(CONVERSATION_A, 1, 10, file(AttachmentType.IMAGE, 3));
        insert(CONVERSATION_A, 2, 30, file(AttachmentType.AUDIO, 3));
        insert(CONVERSATION_A, 3, 20, file(AttachmentType.IMAGE, -1));            // expirada
        insert(CONVERSATION_A, 4, 25, location());                                 // sem arquivo
        insert(CONVERSATION_A, 5, 40, null);                                       // só texto
        insert(CONVERSATION_B, 1, 5, file(AttachmentType.FILE, 2));
        insert(OTHER, 1, 50, file(AttachmentType.VIDEO, 2));                       // fora do escopo pedido

        List<Message> found = messages.findAvailableMedia(Set.of(CONVERSATION_A, CONVERSATION_B), Instant.now(), Long.MAX_VALUE, 10);

        assertThat(found).extracting(Message::getChangeVersion).containsExactly(30L, 10L, 5L);
    }

    @Test
    void cursorAndLimitPageThroughTheList() {
        insert(CONVERSATION_A, 1, 10, file(AttachmentType.IMAGE, 3));
        insert(CONVERSATION_A, 2, 30, file(AttachmentType.AUDIO, 3));
        insert(CONVERSATION_A, 3, 20, file(AttachmentType.FILE, 3));

        assertThat(messages.findAvailableMedia(Set.of(CONVERSATION_A), Instant.now(), Long.MAX_VALUE, 2))
                .extracting(Message::getChangeVersion).containsExactly(30L, 20L);
        assertThat(messages.findAvailableMedia(Set.of(CONVERSATION_A), Instant.now(), 20, 2))
                .extracting(Message::getChangeVersion).containsExactly(10L);
    }

    @Test
    void aMessageDeletedByTheAuthorLosesItsAttachmentAndLeavesTheList() {
        UUID sender = UUID.randomUUID();
        Message saved = messages.insert(message(CONVERSATION_A, 1, 10, sender, file(AttachmentType.IMAGE, 3)));
        assertThat(messages.findAvailableMedia(Set.of(CONVERSATION_A), Instant.now(), Long.MAX_VALUE, 10)).hasSize(1);

        assertThat(messages.deleteIfCurrent(saved.getId(), CONVERSATION_A, sender, 0, 1, 11)).isEqualTo(1);

        Message deleted = messages.findById(saved.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getAttachment()).isNull();
        assertThat(messages.findAvailableMedia(Set.of(CONVERSATION_A), Instant.now(), Long.MAX_VALUE, 10)).isEmpty();
    }

    @Test
    void aLiveMessageCitingTheFileIsFoundAndADeletedOneIsNot() {
        UUID sender = UUID.randomUUID();
        Attachment attachment = file(AttachmentType.IMAGE, 3);
        Message first = messages.insert(message(CONVERSATION_A, 1, 10, sender, attachment));
        messages.insert(message(CONVERSATION_A, 2, 11, sender, attachment));

        assertThat(messages.existsByAttachmentMediaIdAndDeletedFalse(attachment.getMediaId())).isTrue();
        assertThat(messages.existsByAttachmentMediaIdAndDeletedFalse("outro-arquivo")).isFalse();

        messages.deleteIfCurrent(first.getId(), CONVERSATION_A, sender, 0, 1, 12);
        // a outra ainda cita o arquivo
        assertThat(messages.existsByAttachmentMediaIdAndDeletedFalse(attachment.getMediaId())).isTrue();
    }

    @Test
    void theAttachmentRoundTripsThroughMongoWithItsDescription() {
        Attachment attachment = file(AttachmentType.AUDIO, 3);
        attachment.setDurationMs(4500L);
        attachment.setClientPreview("amostras");
        Message saved = messages.insert(message(CONVERSATION_A, 1, 10, UUID.randomUUID(), attachment));

        Attachment read = messages.findById(saved.getId()).orElseThrow().getAttachment();

        assertThat(read.getType()).isEqualTo(AttachmentType.AUDIO);
        assertThat(read.getMediaId()).isEqualTo(attachment.getMediaId());
        assertThat(read.getDurationMs()).isEqualTo(4500L);
        assertThat(read.getClientPreview()).isEqualTo("amostras");
        assertThat(read.getExpiresAt()).isCloseTo(attachment.getExpiresAt(), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
    }

    private void insert(String conversationId, int sequence, long changeVersion, Attachment attachment) {
        messages.insert(message(conversationId, sequence, changeVersion, UUID.randomUUID(), attachment));
    }

    private static Message message(String conversationId, int sequence, long changeVersion, UUID sender, Attachment attachment) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(sender);
        message.setRole("user");
        message.setMessageType(MessageType.USER_TO_GROUP);
        message.setContent("");
        message.setTimestamp(Instant.now());
        message.setSequence(sequence);
        message.setChangeVersion(changeVersion);
        message.setAttachment(attachment);
        return message;
    }

    private static Attachment file(AttachmentType type, long daysUntilExpiry) {
        Attachment attachment = new Attachment();
        attachment.setType(type);
        attachment.setMediaId(UUID.randomUUID().toString());
        attachment.setMimeType("application/octet-stream");
        attachment.setSizeBytes(100L);
        attachment.setExpiresAt(Instant.now().plus(Duration.ofDays(daysUntilExpiry)));
        return attachment;
    }

    private static Attachment location() {
        Attachment attachment = new Attachment();
        attachment.setType(AttachmentType.LOCATION);
        attachment.setLatitude(-23.55);
        attachment.setLongitude(-46.63);
        return attachment;
    }
}
