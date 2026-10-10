package com.garbigo.auth.config;

import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.service.UsernameGenerator;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Order(0)
public class UsernameBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsernameBackfillRunner.class);

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final UsernameGenerator usernameGenerator;

    @Value("${app.username-backfill.enabled:true}")
    private boolean enabled;

    @Value("${app.username-backfill.interval-minutes:10}")
    private long intervalMinutes;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "username-backfill");
        thread.setDaemon(true);
        return thread;
    });

    public UsernameBackfillRunner(MongoTemplate mongoTemplate, UserRepository userRepository,
                                  UsernameGenerator usernameGenerator) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.usernameGenerator = usernameGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        backfill();

        if (intervalMinutes > 0) {
            scheduler.scheduleWithFixedDelay(this::backfillSafely, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
        }
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    private void backfillSafely() {
        try {
            backfill();
        } catch (Exception e) {
            log.error("Username backfill check failed: {}", e.getMessage());
        }
    }

    private void fixDuplicateUsernames() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("username", new Document("$type", "string"))),
                new Document("$sort", new Document("_id", 1)),
                new Document("$group", new Document("_id", "$username")
                        .append("ids", new Document("$push", "$_id"))
                        .append("n", new Document("$sum", 1))),
                new Document("$match", new Document("n", new Document("$gt", 1))));

        List<Document> groups = mongoTemplate.getCollection("users").aggregate(pipeline).into(new ArrayList<>());

        for (Document group : groups) {
            String duplicated = group.getString("_id");
            List<Object> ids = group.getList("ids", Object.class);
            for (int i = 1; i < ids.size(); i++) {
                try {
                    User user = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(ids.get(i))), User.class);
                    if (user == null) {
                        continue;
                    }
                    String replacement = usernameGenerator.generate(null, buildFullName(user), user.getEmail());
                    user.setDisplayUsername(replacement);
                    userRepository.save(user);
                    log.warn("Username '{}' was used by more than one user. User {} now has '{}'",
                            duplicated, user.getId(), replacement);
                } catch (Exception e) {
                    log.error("Could not fix duplicate username '{}' for user {}: {}",
                            duplicated, ids.get(i), e.getMessage());
                }
            }
        }
    }

    private void backfill() {
        fixDuplicateUsernames();

        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("username").exists(false),
                Criteria.where("username").is(null),
                Criteria.where("username").is("")));

        List<User> users = mongoTemplate.find(query, User.class);
        if (users.isEmpty()) {
            return;
        }

        int updated = 0;
        for (User user : users) {
            try {
                String fullName = buildFullName(user);
                user.setDisplayUsername(usernameGenerator.generate(null, fullName, user.getEmail()));
                userRepository.save(user);
                updated++;
            } catch (Exception e) {
                log.error("Could not create a username for user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Username backfill finished: {} of {} users without a username were updated", updated, users.size());
    }

    private String buildFullName(User user) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{user.getFirstName(), user.getMiddleName(), user.getLastName()}) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }
}