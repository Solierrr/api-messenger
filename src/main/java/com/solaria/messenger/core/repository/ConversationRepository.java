package com.solaria.messenger.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.enums.ConversationType;

/**
 * Repositório da entidade {@link Conversation}
 *
 * <p>
 * Concentra as consultas de conversas por participante, por comunidade e por tipo, além das
 * atualizações condicionais por versão que alteram apenas os campos informados: participantes,
 * gestor, reativação e última interação.
 * </p>
 *
 * <p>
 * Principais consultas e atualizações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #findByConversationTypeAndDirectKey(ConversationType, String)} -> conversa direta pelo par
 *         canônico.</li>
 *     <li>{@link #findByParticipantIdsContainingOrderByLastInteractionAtDesc(UUID)} -> conversas do participante.</li>
 *     <li>{@link #updateParticipantsIfCurrent(String, Long, Set, UUID, Instant, Long)} -> altera participação e gestor
 *         na versão observada.</li>
 *     <li>{@link #updateManagerIfCurrent(String, Long, UUID, Instant, Long)} -> transfere a gestão na versão
 *         observada.</li>
 *     <li>{@link #updateLastInteraction(String, Instant)} -> avança a última interação sem regredir o valor.</li>
 * </ul>
 */
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /** Direta pelo par canônico, inclusive quando desativada. */
    Optional<Conversation> findByConversationTypeAndDirectKey(ConversationType type, String directKey);

    /** Reativa somente a identidade e a versão observadas, sem alterar preferências ou participantes. */
    @Query("{ '_id': ?0, 'version': ?1, 'conversation_type': 'DIRECT', 'direct_key': ?2, "
            + "'status': 'DEACTIVATED', 'community_id': null, 'participant_ids': { '$all': ?3, '$size': 2 } }")
    @Update("{ '$set': { 'status': 'ACTIVE', 'version': ?4 } }")
    long reactivateDirectIfCurrent(String id, Long version, String directKey, Set<UUID> participants, Long nextVersion);

    /** Conversas em que o user participa */
    List<Conversation> findByParticipantIdsContainingOrderByLastInteractionAtDesc(UUID userId);

    /** Conversas de um tipo dentro da comunidade, da interação mais recente para a mais antiga. */
    List<Conversation> findByCommunityIdAndConversationTypeOrderByLastInteractionAtDesc(
            String communityId, ConversationType conversationType);

    /** Conversas de um tipo dentro da comunidade das quais o usuário participa, da mais recente para a mais antiga. */
    List<Conversation> findByCommunityIdAndConversationTypeAndParticipantIdsContainingOrderByLastInteractionAtDesc(
            String communityId, ConversationType conversationType, UUID userId);

    /** Indica se o usuário ainda participa de algum subgrupo da comunidade. */
    boolean existsByCommunityIdAndParticipantIdsContaining(String communityId, UUID userId);

    /** Altera participação/gestor sem substituir estado pessoal ou demais campos. */
    @Query("{ '_id': ?0, 'version': ?1 }")
    @Update("{ '$set': { 'participant_ids': ?2, 'manager_user_id': ?3, 'version': ?5 }, "
            + "'$max': { 'last_interaction_at': ?4 } }")
    long updateParticipantsIfCurrent(String id, Long version, Set<UUID> participants, UUID manager,
            Instant interaction, Long nextVersion);

    /** A versão impede que uma transferência substitua outra decisão concorrente. */
    @Query("{ '_id': ?0, 'version': ?1 }")
    @Update("{ '$set': { 'manager_user_id': ?2, 'version': ?4 }, '$max': { 'last_interaction_at': ?3 } }")
    long updateManagerIfCurrent(String id, Long version, UUID manager, Instant interaction, Long nextVersion);

    /** Metadado monotônico, sem substituir participantes, gestor ou status. */
    @Query("{ '_id': ?0 }")
    @Update("{ '$max': { 'last_interaction_at': ?1 } }")
    long updateLastInteraction(String id, Instant interaction);
}
