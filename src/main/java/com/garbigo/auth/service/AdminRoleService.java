package com.garbigo.auth.service;

import com.garbigo.auth.enums.Role;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.AdminEmailPolicy;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class AdminRoleService {

    public enum Source {
        STARTUP_PROVISIONING("by this service's administrator configuration"),
        EMAIL_VERIFICATION("after you verified your email address"),
        SOCIAL_LOGIN("when you signed in with a provider that confirmed your email address"),
        ADMIN_ACTION("by another administrator");

        private final String description;

        Source(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Outcome {
        GRANTED,
        ALREADY_ADMIN,
        NOT_ELIGIBLE,
        NOT_VERIFIED,
        ACCOUNT_UNAVAILABLE,
        PROVIDER_UNVERIFIED
    }

    private final AdminEmailPolicy policy;
    private final UserRepository users;
    private final MongoTemplate mongoTemplate;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final SessionService sessionService;

    public AdminRoleService(AdminEmailPolicy policy, UserRepository users, MongoTemplate mongoTemplate,
                            SecurityAuditService audit, SecurityNotificationService notifications,
                            SessionService sessionService) {
        this.policy = policy;
        this.users = users;
        this.mongoTemplate = mongoTemplate;
        this.audit = audit;
        this.notifications = notifications;
        this.sessionService = sessionService;
    }

    public boolean isEligibleEmail(String email) {
        return policy.isEligible(email);
    }

    public Outcome grantIfEligible(User user, Source source, boolean emailConfirmedByProvider) {
        if (user == null || !policy.isEligible(user.getEmail())) {
            return Outcome.NOT_ELIGIBLE;
        }
        if (user.getRole() == Role.ADMIN) {
            return Outcome.ALREADY_ADMIN;
        }
        if (source == Source.SOCIAL_LOGIN && !emailConfirmedByProvider) {
            audit.record(SecurityEventType.ADMIN_GRANT_SKIPPED, user.getId(), null,
                    "The sign-in provider did not confirm the email address");
            return Outcome.PROVIDER_UNVERIFIED;
        }
        if (!user.isVerified()) {
            return Outcome.NOT_VERIFIED;
        }
        if (user.isArchived() || !user.isActive()) {
            return Outcome.ACCOUNT_UNAVAILABLE;
        }

        Role previous = user.getRole();
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(user.getId()).and("role").ne(Role.ADMIN)),
                new Update().set("role", Role.ADMIN), User.class);
        user.setRole(Role.ADMIN);

        if (result != null && result.getModifiedCount() == 0) {
            return Outcome.ALREADY_ADMIN;
        }

        audit.record(SecurityEventType.ADMIN_ROLE_GRANTED, user.getId(), null,
                "source=" + source.name() + ", previousRole=" + previous);
        notifications.notifyAdminRoleGranted(user.getId(), source.getDescription(), previous.name());
        return Outcome.GRANTED;
    }

    public void changeRole(User actor, User target, Role newRole, String reason) {
        Role previous = target.getRole();
        if (newRole == null || previous == newRole) {
            return;
        }

        if (previous == Role.ADMIN && newRole != Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new CustomException("You can't remove the last admin account.");
        }

        target.setRole(newRole);
        users.save(target);

        audit.record(SecurityEventType.ADMIN_ROLE_CHANGED, target.getId(), actor == null ? null : actor.getId(),
                "from=" + previous + ", to=" + newRole + ", reason=" + reason);

        if (newRole == Role.ADMIN) {
            notifications.notifyAdminRoleGranted(target.getId(), Source.ADMIN_ACTION.getDescription(), previous.name());
        } else {
            notifications.notifyRoleChanged(target.getId(), previous.name(), newRole.name());
        }

        if (previous == Role.ADMIN) {
            sessionService.revokeAll(target.getId(), SessionRevokeReason.ROLE_CHANGED);
        }
    }
}
