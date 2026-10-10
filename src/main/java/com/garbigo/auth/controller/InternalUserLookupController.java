package com.garbigo.auth.controller;

import com.garbigo.auth.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/internal/users")
public class InternalUserLookupController {

    private final UserRepository userRepository;

    public InternalUserLookupController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{email}/details")
    public ResponseEntity<Map<String, Object>> details(@PathVariable String email) {
        return userRepository.findByEmail(email)
                .map(u -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("id", u.getId());
                    body.put("email", u.getEmail());
                    body.put("role", u.getRole());
                    body.put("active", u.isActive());
                    body.put("verified", u.isVerified());
                    body.put("firstName", u.getFirstName());
                    body.put("middleName", u.getMiddleName());
                    body.put("lastName", u.getLastName());
                    body.put("fullName", fullName(u.getFirstName(), u.getMiddleName(), u.getLastName()));
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/validate-email/{email}")
    public Map<String, Object> validateEmail(@PathVariable String email) {
        Map<String, Object> result = new LinkedHashMap<>();
        var found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            result.put("valid", false);
            result.put("reason", "NOT_FOUND");
            return result;
        }
        var u = found.get();
        String reason = u.isArchived() ? "ARCHIVED"
                : !u.isActive() ? "INACTIVE"
                : !u.isVerified() ? "UNVERIFIED" : null;
        result.put("valid", reason == null);
        if (reason != null) {
            result.put("reason", reason);
        }
        return result;
    }

    private static String fullName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{first, middle, last}) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}