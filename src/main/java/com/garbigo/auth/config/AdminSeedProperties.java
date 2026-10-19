package com.garbigo.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@ConfigurationProperties(prefix = "app.admin")
public class AdminSeedProperties {

    private boolean seedEnabled = true;
    private int minPasswordLength = 8;
    private List<Account> seed = new ArrayList<>();

    @Data
    public static class Account {
        private String email;
        private String username;
        private String firstName;
        private String middleName;
        private String lastName;
        private String phoneNumber;
        private String password;

        public boolean empty() {
            return isEmpty(email) && isEmpty(username) && isEmpty(firstName) && isEmpty(middleName)
                    && isEmpty(lastName) && isEmpty(phoneNumber) && isEmpty(password);
        }

        private static boolean isEmpty(String value) {
            return value == null || value.isBlank();
        }
    }

    public String seededEmails() {
        return seed.stream()
                .filter(account -> account.getEmail() != null && !account.getEmail().isBlank())
                .map(account -> account.getEmail().trim())
                .collect(Collectors.joining(","));
    }
}
