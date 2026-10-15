package com.solaria.messenger.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.model.enums.NpsClassification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
