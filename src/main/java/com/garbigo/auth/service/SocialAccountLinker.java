package com.garbigo.auth.service;

import com.garbigo.auth.enums.Role;
import com.garbigo.auth.enums.SecurityEventType;
import com.garbigo.auth.enums.SessionRevokeReason;
import com.garbigo.auth.enums.SignInMethod;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SocialAccountLinker {

    public record Identity(String email, String displayName, String preferredUsername,
                           boolean emailConfirmedByProvider, SignInMethod provider) {
    }

    private final UserRepository users;
    private final UsernameGenerator usernameGenerator;
    private final AdminRoleService adminRoleService;
    private final SessionService sessionService;
    private final SecurityAuditService audit;

    public SocialAccountLinker(UserRepository users, UsernameGenerator usernameGenerator,
                               AdminRoleService adminRoleService, SessionService sessionService,
                               SecurityAuditService audit) {
        this.users = users;
        this.usernameGenerator = usernameGenerator;
        this.adminRoleService = adminRoleService;
        this.sessionService = sessionService;
        this.audit = audit;
    }

    public User resolveAndPromote(Identity identity) {
        User user = findExisting(identity.email());
        boolean credentialsCleared = false;

        if (user == null) {
            if (!identity.emailConfirmedByProvider() && adminRoleService.isEligibleEmail(identity.email())) {
                throw new CustomException("For security, this email address can't be used to create an account "
                        + "with " + identity.provider().getLabel() + ". Please sign up with your email and password.");
            }
            user = newUser(identity);
        } else {
            credentialsCleared = guardExisting(user, identity);
        }

        if (user.getDisplayUsername() == null) {
            user.setDisplayUsername(usernameGenerator.generate(identity.preferredUsername(), identity.displayName(),
                    identity.email()));
        }
        user = users.save(user);

        if (credentialsCleared) {
            sessionService.revokeAll(user.getId(), SessionRevokeReason.SOCIAL_ACCOUNT_LINKED);
        }

        adminRoleService.grantIfEligible(user, AdminRoleService.Source.SOCIAL_LOGIN, identity.emailConfirmedByProvider());
        return user;
    }

    private boolean guardExisting(User user, Identity identity) {
        boolean credentialsCleared = false;
        if (user.isArchived() || !user.isActive()) {
            throw new CustomException("This account is currently unavailable. Please contact support for help.");
        }

        boolean privileged = isPrivileged(user.getRole()) || adminRoleService.isEligibleEmail(user.getEmail());
        if (privileged && !identity.emailConfirmedByProvider()) {
            throw new CustomException("For security, this account can't be opened with "
                    + identity.provider().getLabel() + ". Please sign in another way.");
        }

        if (!user.isVerified()) {
            if (!identity.emailConfirmedByProvider()) {
                throw new CustomException("Please verify your email using the link we sent you, then try again.");
            }
            if (user.getPassword() != null) {
                user.setPassword(null);
                credentialsCleared = true;
                audit.record(SecurityEventType.SOCIAL_LINK_CREDENTIALS_CLEARED, user.getId(), null,
                        "provider=" + identity.provider().name());
            }
            user.setVerified(true);
        }
        return credentialsCleared;
    }

    private User findExisting(String email) {
        User exact = users.findByEmail(email).orElse(null);
        if (exact != null) {
            return exact;
        }
        List<User> matches = users.findAllByEmailIgnoreCase(email);
        if (matches.size() > 1) {
            throw new CustomException("We couldn't sign you in with this account. Please contact support for help.");
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private User newUser(Identity identity) {
        User user = new User();
        user.setEmail(identity.email());

        String[] nameParts = identity.displayName().split(" ");
        user.setFirstName(nameParts[0]);
        if (nameParts.length > 1) {
            user.setLastName(nameParts[nameParts.length - 1]);
        }

        user.setRole(Role.CLIENT);
        user.setVerified(true);
        return user;
    }

    private static boolean isPrivileged(Role role) {
        return role != null && role != Role.CLIENT && role != Role.COLLECTOR;
    }
}
