package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.core.model.enums.Environment;
import com.solaria.messenger.core.model.enums.NpsClassification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com um registro de NPS (Net Promoter Score) já persistido.
 *
 * <p>Retornado por {@code POST /messaging/nps}. Inclui a nota, a classificação do NPS e o ambiente
 * de origem da resposta.</p>
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpsResponseDTO {

    private String id;
    private String conversationId;
    private UUID userId;
    private int score;
    private NpsClassification classification;
    private Environment environment;
    private Instant timestamp;
}
