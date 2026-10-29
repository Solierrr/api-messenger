package com.solaria.messenger.core.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.core.model.enums.MessageType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * representa uma mensagem trocada em uma conversa.
 *
 * <p>Coleção {@code messages}. Indexada por {@code conversation_id}, pelo índice composto único
 * {@code conversation_id + sequence}, que garante a ordem das mensagens dentro de cada conversa, e pelo índice
 * {@code conversation_id + change_version}, que atende o catch-up de {@code GET /messaging/updates}.
 * {@code mutationEpoch} versiona as alterações da mensagem. {@code changeVersion} guarda a versão do relógio de
 * sincronização da última mudança (envio, edição ou exclusão). Documento sem o campo vale 0.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "conversation_sequence_idx",
                def = "{'conversation_id': 1, 'sequence': 1}", unique = true),
        @CompoundIndex(name = "conversation_change_version_idx",
                def = "{'conversation_id': 1, 'change_version': 1}")
})
public class Message {

    @Id
    private String id;

    @Indexed
    @Field("conversation_id")
    private String conversationId;

    @Field("sender_id")
    private UUID senderId;

    @Field("client_message_id")
    private UUID clientMessageId;

    private String role;

    @Field("message_type")
    private MessageType messageType;

    private String content;

    @Field("edited")
    private boolean edited;

    @Field("deleted")
    private boolean deleted;

    @Field("mutation_epoch")
    private long mutationEpoch;

    @Field("change_version")
    private long changeVersion;

    private MessageMetadata metadata;

    private Instant timestamp;

    @Field("sequence")
    private int sequence;
}
