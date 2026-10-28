package com.solaria.messenger.core.contract.request;

import tools.jackson.databind.JsonNode;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

/** PATCH fields retain whether a JSON property was absent or explicitly null. */
public final class ConversationStatePatchRequestDTO {

    private PatchField<Integer> lastReadSequence = PatchField.absent();
    private PatchField<Boolean> muted = PatchField.absent();
    private PatchField<Boolean> archived = PatchField.absent();

    /** Registra que {@code lastReadSequence} veio no corpo, inclusive como {@code null} ou tipo inválido. */
    @JsonSetter(value = "lastReadSequence", nulls = Nulls.SET)
    public void acceptLastReadSequence(JsonNode value) {
        lastReadSequence = new PatchField<>(true, asInteger(value));
    }

    /** Registra que {@code muted} veio no corpo, inclusive como {@code null} ou tipo inválido. */
    @JsonSetter(value = "muted", nulls = Nulls.SET)
    public void acceptMuted(JsonNode value) {
        muted = new PatchField<>(true, asBoolean(value));
    }

    /** Registra que {@code archived} veio no corpo, inclusive como {@code null} ou tipo inválido. */
    @JsonSetter(value = "archived", nulls = Nulls.SET)
    public void acceptArchived(JsonNode value) {
        archived = new PatchField<>(true, asBoolean(value));
    }

    /** Cursor de leitura informado, ou ausente quando o campo foi omitido. */
    public PatchField<Integer> lastReadSequence() {
        return lastReadSequence;
    }

    /** Preferência de silenciar informada, ou ausente quando o campo foi omitido. */
    public PatchField<Boolean> muted() {
        return muted;
    }

    /** Preferência de arquivar informada, ou ausente quando o campo foi omitido. */
    public PatchField<Boolean> archived() {
        return archived;
    }

    /** Converte para inteiro só valores integrais que cabem em {@code int}; o resto vira {@code null}. */
    private static Integer asInteger(JsonNode value) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            return null;
        }
        return value.intValue();
    }

    /** Converte para booleano só valores JSON booleanos; o resto vira {@code null}. */
    private static Boolean asBoolean(JsonNode value) {
        return value != null && value.isBoolean() ? value.booleanValue() : null;
    }
}
