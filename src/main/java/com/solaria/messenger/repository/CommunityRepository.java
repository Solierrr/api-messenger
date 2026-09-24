package com.solaria.messenger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.solaria.messenger.model.ProjectCommunity;

public interface CommunityRepository extends MongoRepository<ProjectCommunity, String> {

    Optional<ProjectCommunity> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);

    /** Comunidades das quais o usuário é membro */
    @Query("{ 'members.user_id': ?0 }")
    List<ProjectCommunity> findAllByMemberId(UUID userId);
}
