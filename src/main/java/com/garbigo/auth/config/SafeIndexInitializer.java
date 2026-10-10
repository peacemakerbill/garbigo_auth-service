package com.garbigo.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver.IndexDefinitionHolder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.stereotype.Component;

@Component
public class SafeIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SafeIndexInitializer.class);

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    public SafeIndexInitializer(MongoTemplate mongoTemplate, MongoMappingContext mappingContext) {
        this.mongoTemplate = mongoTemplate;
        this.mappingContext = mappingContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

        for (MongoPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
            if (!entity.isAnnotationPresent(Document.class)) {
                continue;
            }
            for (IndexDefinitionHolder holder : resolver.resolveIndexFor(entity.getTypeInformation())) {
                try {
                    mongoTemplate.indexOps(holder.getCollection()).createIndex(holder.getIndexDefinition());
                } catch (Exception e) {
                    log.error("Could not create index {} on collection '{}'. The service will keep running, but "
                                    + "this rule is not enforced until the existing data is cleaned. Reason: {}",
                            holder.getIndexDefinition().getIndexKeys(), holder.getCollection(), e.getMessage());
                }
            }
        }
    }
}