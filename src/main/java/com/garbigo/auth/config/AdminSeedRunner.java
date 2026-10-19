package com.garbigo.auth.config;

import com.garbigo.auth.enums.Role;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.service.SecurityAuditService;
import com.garbigo.auth.service.UsernameGenerator;
import com.garbigo.auth.util.EmailAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(10)
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    public record Summary(int created, int alreadyExisting, int skipped) {
    }

    private final AdminSeedProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final SecurityAuditService audit;

    public AdminSeedRunner(AdminSeedProperties properties, UserRepository users, PasswordEncoder passwordEncoder,
                           UsernameGenerator usernameGenerator, SecurityAuditService audit) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.usernameGenerator = usernameGenerator;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (Exception e) {
            log.error("Administrator account setup could not finish ({}). The service keeps running.",
                    e.getClass().getSimpleName());
        }
    }

    public Summary seed() {
        if (!properties.isSeedEnabled()) {
            log.info("Administrator account setup from configuration is switched off.");
            return new Summary(0, 0, 0);
        }

        int created = 0;
        int existing = 0;
        int skipped = 0;

        List<AdminSeedProperties.Account> accounts = properties.getSeed();
        for (int i = 0; i < accounts.size(); i++) {
            AdminSeedProperties.Account account = accounts.get(i);
            if (account == null || account.empty()) {
                continue;
            }
            int slot = i + 1;

            String email = EmailAddresses.normalize(account.getEmail());
            if (email == null) {
                log.warn("Administrator account {} was skipped because its email address is missing or not valid.", slot);
                skipped++;
                continue;
            }

            if (!users.findAllByEmailIgnoreCase(email).isEmpty()) {
                existing++;
                log.info("Administrator account {} ({}) already exists. Its password was not changed.",
                        slot, EmailAddresses.mask(email));
                continue;
            }

            String problem = passwordProblem(account.getPassword());
            if (problem != null) {
                log.warn("Administrator account {} ({}) was skipped: {}", slot, EmailAddresses.mask(email), problem);
                skipped++;
                continue;
            }

            try {
                users.save(build(account, email));
                created++;
                log.info("Administrator account {} created for {}.", slot, EmailAddresses.mask(email));
                audit.record(SecurityEventType.ADMIN_ACCOUNT_SEEDED, null, null, "slot=" + slot);
            } catch (Exception e) {
                skipped++;
                log.error("Administrator account {} ({}) could not be created ({}).", slot,
                        EmailAddresses.mask(email), e.getClass().getSimpleName());
            }
        }

        log.info("Administrator account setup finished: created={}, alreadyExisting={}, skipped={}",
                created, existing, skipped);
        return new Summary(created, existing, skipped);
    }

    String passwordProblem(String password) {
        if (password == null || password.isBlank()) {
            return "no password is set for it in the configuration.";
        }
        if (password.length() < properties.getMinPasswordLength()) {
            return "its password is shorter than " + properties.getMinPasswordLength() + " characters.";
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return "its password must contain both letters and numbers.";
        }
        return null;
    }

    private User build(AdminSeedProperties.Account account, String email) {
        String first = clean(account.getFirstName());
        String middle = clean(account.getMiddleName());
        String last = clean(account.getLastName());
        if (first == null) {
            first = "Admin";
        }

        StringBuilder fullName = new StringBuilder(first);
        if (middle != null) {
            fullName.append(' ').append(middle);
        }
        if (last != null) {
            fullName.append(' ').append(last);
        }

        User user = new User();
        user.setEmail(email);
        user.setFirstName(first);
        user.setMiddleName(middle);
        user.setLastName(last);
        String phone = clean(account.getPhoneNumber());
        if (phone != null) {
            user.setPhoneNumber(phone);
        }
        user.setDisplayUsername(usernameGenerator.generate(clean(account.getUsername()), fullName.toString(), email));
        user.setPassword(passwordEncoder.encode(account.getPassword()));
        user.setRole(Role.ADMIN);
        user.setVerified(true);
        user.setActive(true);
        user.setArchived(false);
        return user;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
