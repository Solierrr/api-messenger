package com.solaria.messenger.rest.observability;

import org.bson.Document;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component("mongoHealthIndicator")
public class MongoDatabaseHealthIndicator extends AbstractHealthIndicator {

    private final MongoTemplate mongoTemplate;

    public MongoDatabaseHealthIndicator(MongoTemplate mongoTemplate) {
        super("MongoDB health check failed");
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        Document result = mongoTemplate.executeCommand("{ hello: 1 }");
        builder.up().withDetail("maxWireVersion", result.getInteger("maxWireVersion"));
    }
}
