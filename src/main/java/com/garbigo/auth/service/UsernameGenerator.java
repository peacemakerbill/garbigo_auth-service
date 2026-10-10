package com.garbigo.auth.service;

import com.garbigo.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

@Service
public class UsernameGenerator {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 20;
    private static final int SEQUENTIAL_ATTEMPTS = 50;

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public UsernameGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String generate(String preferred, String fullName, String email) {
        String base = clean(preferred);
        if (base.length() < MIN_LENGTH) {
            base = clean(fullName);
        }
        if (base.length() < MIN_LENGTH && email != null) {
            int at = email.indexOf('@');
            base = clean(at > 0 ? email.substring(0, at) : email);
        }
        if (base.length() < MIN_LENGTH) {
            base = "user";
        }

        if (isFree(base)) {
            return base;
        }

        for (int i = 2; i < SEQUENTIAL_ATTEMPTS + 2; i++) {
            String candidate = withSuffix(base, String.valueOf(i));
            if (isFree(candidate)) {
                return candidate;
            }
        }

        while (true) {
            String candidate = withSuffix(base, String.valueOf(1000 + random.nextInt(9000)));
            if (isFree(candidate)) {
                return candidate;
            }
        }
    }

    private boolean isFree(String candidate) {
        return userRepository.findByDisplayUsername(candidate).isEmpty();
    }

    private String withSuffix(String base, String suffix) {
        int room = MAX_LENGTH - suffix.length();
        String trimmed = base.length() > room ? base.substring(0, room) : base;
        return trimmed + suffix;
    }

    private String clean(String input) {
        if (input == null) {
            return "";
        }
        String ascii = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        String cleaned = ascii
                .replaceAll("[^a-z0-9._]+", "")
                .replaceAll("[._]{2,}", ".")
                .replaceAll("^[._]+|[._]+$", "");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH).replaceAll("[._]+$", "");
        }
        return cleaned;
    }
}