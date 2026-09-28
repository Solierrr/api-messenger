package com.solaria.messenger.core.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadados opcionais de uma mensagem, produzidos pelo fluxo do chatbot: identificador do turno,
 * indicação de conteúdo anonimizado, especialistas consultados e etapas do fluxo executadas.
 *
 * <p>Embutido em {@link Message} e no payload de ingestão de mensagens do chatbot.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageMetadata {

    private String turnId;

    private Boolean contentAnonymized;

    private List<String> specialistsUsed;

    private List<String> workflowSteps;
}
