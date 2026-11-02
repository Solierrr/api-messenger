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
 * {@code conversation_id + change_version}, que atende o catch-up de {@code GET /messaging/updates}, e pelo índice
 * esparso {@code conversation_id + attachment.expires_at}, que atende a lista de mídias ainda disponíveis.
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
                def = "{'conversation_id': 1, 'change_version': 1}"),
        @CompoundIndex(name = "conversation_attachment_expiry_idx",
                def = "{'conversation_id': 1, 'attachment.expires_at': 1}", sparse = true),
        @CompoundIndex(name = "attachment_media_id_idx", def = "{'attachment.media_id': 1}", sparse = true)
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

    /** Anexo da mensagem, nulo quando só tem texto ou quando foi excluída. */
    private Attachment attachment;

    private Instant timestamp;

    @Field("sequence")
    private int sequence;
}
