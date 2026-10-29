package com.solaria.messenger.core.model;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * estado pessoal de um usuário em uma conversa: última leitura, silenciamento, arquivamento, baseline de admissão e
 * versão em que perdeu a participação.
 *
 * <p>{@code leftAtVersion} é nulo enquanto o usuário participa. Quando ele sai ou é removido, recebe a versão do
 * relógio de sincronização daquele momento, e o catch-up devolve as mensagens da conversa até essa versão. Na
 * readmissão o estado é reaproveitado e o campo é limpo.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #hasValidBaseline()} -> verifica se o estado possui baseline e campos obrigatórios</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_user_states")
@CompoundIndex(name = "uq_conversation_user_state",
        def = "{'conversation_id': 1, 'user_id': 1}", unique = true)
public class ConversationUserState {

    @Id
    private String id;

    @Field("conversation_id")
    private String conversationId;

    @Field("user_id")
    private UUID userId;

    @Field("last_read_sequence")
    private Integer lastReadSequence;

    private Boolean muted;

    private Boolean archived;

    @Field("baseline_sequence")
    private Integer baselineSequence;

    @Field("left_at_version")
    private Long leftAtVersion;

    /** O baseline é dado de admissão persistido, nunca calculado por leitura tardia */
    public boolean hasValidBaseline() {
        return conversationId != null && userId != null && baselineSequence != null && baselineSequence >= 0
                && lastReadSequence != null && lastReadSequence >= baselineSequence && muted != null && archived != null;
    }
}
