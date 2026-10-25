package com.solaria.messenger.core.mapper;

import com.solaria.messenger.core.contract.response.NpsResponseDTO;
import com.solaria.messenger.core.model.Nps;

/** Mapeamento único da entidade persistida para o contrato público, sem consultas ou autorização */
public final class NpsMapper {
    private NpsMapper() { }

    /** Converte a avaliação NPS persistida no contrato de resposta */
    public static NpsResponseDTO toResponse(Nps nps) {
        return NpsResponseDTO.builder()
                .id(nps.getId())
                .conversationId(nps.getConversationId())
                .userId(nps.getUserId())
                .score(nps.getScore())
                .classification(nps.getClassification())
                .environment(nps.getEnvironment())
                .timestamp(nps.getTimestamp())
                .build();
    }
}
