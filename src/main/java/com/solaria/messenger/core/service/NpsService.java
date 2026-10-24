package com.solaria.messenger.core.service;

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

/**
 * Serviço de registro de NPS (Net Promoter Score) das conversas com o chatbot.
 *
 * <p>Garante que a conversa existe, que o usuário autenticado participa dela, que é do tipo
 * {@code CHAT_BOT} e que ainda não recebeu NPS. A classificação é derivada da nota.</p>
 *
 * <p><b>Depende de:</b></p>
 * <ul>
 *     <li>{@link NpsRepository} - persistência das respostas de NPS.</li>
 *     <li>{@link ConversationRepository} - leitura da conversa avaliada.</li>
 *     <li>{@link RbacAuthorizationService} - identificação do usuário e checagem de participação.</li>
 * </ul>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #create(NpsRequestDTO)} - registra o NPS único de uma conversa com o chatbot.</li>
 * </ul>
 */
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

    /** Registra o NPS único de uma conversa CHAT_BOT da qual o usuário participa. */
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
}
