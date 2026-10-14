package com.solaria.messenger.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.NpsRequestDTO;
import com.solaria.messenger.dto.response.NpsResponseDTO;
import com.solaria.messenger.exception.BusinessRuleException;
import com.solaria.messenger.exception.DuplicateResourceException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.Nps;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.NpsClassification;
import com.solaria.messenger.repository.ConversationRepository;
import com.solaria.messenger.repository.NpsRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@Service
public class NpsService {

    private final NpsRepository npsRepository;
    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;

    public NpsService(NpsRepository npsRepository,
                      ConversationRepository conversationRepository,
                      RbacAuthorizationService rbac) {
        this.npsRepository = npsRepository;
        this.conversationRepository = conversationRepository;
        this.rbac = rbac;
    }

    public NpsResponseDTO create(NpsRequestDTO dto) {
        UUID userId = rbac.currentUserId();

        Conversation conversation = conversationRepository.findById(dto.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversa não encontrada com ID: " + dto.getConversationId()));
        rbac.requireParticipant(conversation.getParticipantIds());

        if (conversation.getConversationType() != ConversationType.CHAT_BOT) {
            throw new BusinessRuleException("O NPS só pode ser respondido em conversas com o chatbot.");
        }
        if (npsRepository.existsByConversationId(conversation.getId())) {
            throw new DuplicateResourceException("Esta conversa já foi avaliada.");
        }

        Nps nps = new Nps();
        nps.setConversationId(conversation.getId());
        nps.setUserId(userId);
        nps.setScore(dto.getScore());
        nps.setClassification(NpsClassification.fromScore(dto.getScore()));
        nps.setEnvironment(dto.getEnvironment());
        nps.setTimestamp(Instant.now());

        try {
            return toResponse(npsRepository.save(nps));
        } catch (DuplicateKeyException ex) {
            // Duas requisições simultâneas passam pelo existsBy; o índice único barra a segunda.
            throw new DuplicateResourceException("Esta conversa já foi avaliada.");
        }
    }

    private NpsResponseDTO toResponse(Nps nps) {
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
