package com.solaria.messenger.core.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.enums.CommunityStatus;

/**
 * Repositório Spring Data MongoDB da comunidade de projeto ({@link ProjectCommunity}).
 *
 * <p>Concentra as consultas por projeto e por membro, e as atualizações condicionais por versão (CAS)
 * de membros, status e dados da comunidade, que impedem sobrescrever alterações concorrentes.</p>
 *
 * <p>Principais consultas e atualizações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #findByProjectId(UUID)} -> comunidade de um projeto técnico.</li>
 *     <li>{@link #findAllByMemberId(UUID)} -> comunidades das quais o usuário é membro.</li>
 *     <li>{@link #updateMembersIfCurrent(String, Long, List, Instant, Long)} -> substitui os membros na versão observada.</li>
 *     <li>{@link #updateDetailsIfCurrent(String, Long, String, String, Instant, Long)} -> altera nome e descrição na versão observada.</li>
 *     <li>{@link #updateRosterIfCurrent(String, Long, List, CommunityStatus, Instant, Long)} -> substitui membros e status na versão observada.</li>
 * </ul>
 */
public interface CommunityRepository extends MongoRepository<ProjectCommunity, String> {

    /** Comunidade de um projeto técnico, se existir. */
    Optional<ProjectCommunity> findByProjectId(UUID projectId);

    /** Indica se o projeto já tem comunidade. */
    boolean existsByProjectId(UUID projectId);

    /** Comunidades das quais o usuário é membro */
    @Query("{ 'members.user_id': ?0 }")
    List<ProjectCommunity> findAllByMemberId(UUID userId);

    /** Substitui os membros somente se a versão observada ainda for a atual (CAS). */
    @Query("{ '_id': ?0, 'version': ?1 }")
    @Update("{ '$set': { 'members': ?2, 'updated_at': ?3, 'version': ?4 } }")
    long updateMembersIfCurrent(String id, Long version, List<CommunityMember> members, Instant updatedAt, Long nextVersion);

    /** Altera nome e descrição somente se a versão observada ainda for a atual (CAS). */
    @Query("{ '_id': ?0, 'version': ?1 }")
    @Update("{ '$set': { 'name': ?2, 'description': ?3, 'updated_at': ?4, 'version': ?5 } }")
    long updateDetailsIfCurrent(String id, Long version, String name, String description, Instant updatedAt,
            Long nextVersion);

    /** Substitui membros e status de uma vez somente se a versão observada ainda for a atual (CAS). */
    @Query("{ '_id': ?0, 'version': ?1 }")
    @Update("{ '$set': { 'members': ?2, 'status': ?3, 'updated_at': ?4, 'version': ?5 } }")
    long updateRosterIfCurrent(String id, Long version, List<CommunityMember> members, CommunityStatus status,
            Instant updatedAt, Long nextVersion);
}
