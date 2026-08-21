package com.solaria.messenger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Rating;

public interface RatingRepository extends MongoRepository<Rating, String> {

    List<Rating> findByEvaluatedId(UUID evaluatedId);

    List<Rating> findByEvaluatorId(UUID evaluatorId);
}
