package com.solaria.messenger.core.repository;

import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoTemplateDependsOnIndexMigrations extends AbstractDependsOnBeanFactoryPostProcessor {

    public MongoTemplateDependsOnIndexMigrations() {
        super(MongoTemplate.class, MessageSequenceIndexRepository.class);
    }
}
