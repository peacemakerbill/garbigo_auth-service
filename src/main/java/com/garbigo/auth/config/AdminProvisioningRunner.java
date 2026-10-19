package com.garbigo.auth.config;

import com.garbigo.auth.enums.Role;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.AdminEmailPolicy;
import com.garbigo.auth.service.AdminRoleService;
import com.garbigo.auth.service.SecurityAuditService;
import com.garbigo.auth.service.SecurityNotificationService;
import com.garbigo.auth.util.EmailAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(20)
public class AdminProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioningRunner.class);

    public record Summary(int granted, int alreadyAdmin, int waitingForVerification, int unavailable, int invited) {
    }

    private final AdminEmailPolicy policy;
    private final UserRepository users;
    private final AdminRoleService roleService;
    private final SecurityAuditService audit;
    private final SecurityNotificationService notifications;
    private final SecurityProperties properties;

    public AdminProvisioningRunner(AdminEmailPolicy policy, UserRepository users, AdminRoleService roleService,
                                   SecurityAuditService audit, SecurityNotificationService notifications,
                                   SecurityProperties properties) {
        this.policy = policy;
        this.users = users;
        this.roleService = roleService;
        this.audit = audit;
        this.notifications = notifications;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            provision();
        } catch (Exception e) {
            log.error("Administrator provisioning could not finish ({}). The service keeps running.",
                    e.getClass().getSimpleName());
        }
    }

    public Summary provision() {
        for (String invalid : policy.invalidEntriesMasked()) {
            log.warn("Ignored an invalid administrator email in the configuration: {}", invalid);
            audit.record(SecurityEventType.ADMIN_CONFIG_INVALID_ENTRY, null, null, "entry=" + invalid);
        }

        int granted = 0;
        int already = 0;
        int waiting = 0;
        int unavailable = 0;
        int invited = 0;

        if (policy.initialAdmins().isEmpty()) {
            log.info("No initial administrator emails are configured (INITIAL_ADMIN_EMAILS is empty).");
        }

        for (String email : policy.initialAdmins()) {
            List<User> matches = users.findAllByEmailIgnoreCase(email);

            if (matches.isEmpty()) {
                if (invite(email)) {
                    invited++;
                }
                continue;
            }

            for (User user : matches) {
                AdminRoleService.Outcome outcome = roleService.grantIfEligible(user,
                        AdminRoleService.Source.STARTUP_PROVISIONING, true);
                switch (outcome) {
                    case GRANTED -> {
                        granted++;
                        log.info("Administrator access granted to {}", EmailAddresses.mask(email));
                    }
                    case ALREADY_ADMIN -> already++;
                    case NOT_VERIFIED -> {
                        waiting++;
                        log.info("{} has not verified their email yet. Administrator access is applied after verification.",
                                EmailAddresses.mask(email));
                    }
                    case ACCOUNT_UNAVAILABLE -> {
                        unavailable++;
                        log.warn("{} is inactive or archived, so administrator access was not applied.",
                                EmailAddresses.mask(email));
                    }
                    default -> {
                    }
                }
            }
        }

        log.info("Administrator provisioning finished: granted={}, alreadyAdmin={}, waitingForVerification={}, "
                + "unavailable={}, invited={}", granted, already, waiting, unavailable, invited);

        reportDrift();
        return new Summary(granted, already, waiting, unavailable, invited);
    }

    private boolean invite(String email) {
        Duration resendAfter = Duration.ofDays(Math.max(1, properties.getAdminInvitationResendDays()));
        if (audit.happenedRecentlyForEmail(SecurityEventType.ADMIN_INVITATION_SENT, email, resendAfter)) {
            return false;
        }
        notifications.sendAdminInvitation(email);
        log.info("Administrator invitation queued for {}", EmailAddresses.mask(email));
        return true;
    }

    private void reportDrift() {
        List<User> admins = users.findByRole(Role.ADMIN);
        List<String> outside = admins.stream()
                .map(User::getEmail)
                .filter(email -> !policy.isEligible(email))
                .map(EmailAddresses::mask)
                .collect(Collectors.toList());

        if (outside.isEmpty()) {
            return;
        }

        log.warn("{} administrator account(s) are not listed in INITIAL_ADMIN_EMAILS or AUTO_ADMIN_EMAILS. "
                        + "They keep their access because roles are never removed automatically. "
                        + "Review them if this is unexpected: {}",
                outside.size(), outside.stream().limit(10).collect(Collectors.joining(", ")));
        audit.record(SecurityEventType.ADMIN_CONFIG_DRIFT, null, null, "count=" + outside.size());
    }
}
