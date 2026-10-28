package com.solaria.messenger.core.usecase.nps;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.NpsRequestDTO;
import com.solaria.messenger.core.contract.response.NpsResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.DuplicateResourceException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Nps;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.NpsClassification;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.NpsRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.mapper.NpsMapper.toResponse;

@Service
public class CreateNpsUseCase {

    private final NpsRepository npsRepository;
    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;

    public CreateNpsUseCase(NpsRepository npsRepository,
                            ConversationRepository conversationRepository,
                            RbacAuthorizationService rbac) {
        this.npsRepository = npsRepository;
        this.conversationRepository = conversationRepository;
        this.rbac = rbac;
    }

    /** Registra o NPS único de uma conversa CHAT_BOT da qual o usuário participa. */
    public NpsResponseDTO execute(NpsRequestDTO dto) {
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
}
