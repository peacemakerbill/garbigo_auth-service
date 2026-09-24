package com.garbigo.auth.service;

import com.garbigo.auth.dto.AuthResponse;
import com.garbigo.auth.dto.SocialLoginRequest;
import com.garbigo.auth.exception.CustomException;
import com.garbigo.auth.model.Role;
import com.garbigo.auth.model.User;
import com.garbigo.auth.repository.UserRepository;
import com.garbigo.auth.security.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SocialAuthService {

    // Shared, reusable descriptor for a plain JSON-object response body.
    // Using this with RestTemplate.exchange(...) instead of getForEntity(url, Map.class)
    // gets a properly generic Map<String, Object> back instead of a raw Map.
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    // Same idea, for endpoints that return a JSON array of objects (e.g. GitHub's
    // /user/emails), so we don't fall back to a raw List either.
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() {};

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${facebook.app-id}")
    private String facebookAppId;

    @Value("${facebook.app-secret}")
    private String facebookAppSecret;

    @Value("${github.client-id}")
    private String githubClientId;

    @Value("${github.client-secret}")
    private String githubClientSecret;

    public SocialAuthService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse googleLogin(SocialLoginRequest request) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getToken());
            if (idToken == null) {
                throw new CustomException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            User user = findOrCreateSocialUser(email, name != null ? name : "Google User");
            user.setVerified(true);
            userRepository.save(user);

            return buildAuthResponse(user);
        } catch (Exception e) {
            throw new CustomException("Google login failed: " + e.getMessage());
        }
    }

    public AuthResponse facebookLogin(SocialLoginRequest request) {
        try {
            String appAccessToken = facebookAppId + "|" + facebookAppSecret;
            String debugUrl = "https://graph.facebook.com/debug_token?input_token="
                    + request.getToken() + "&access_token=" + appAccessToken;

            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(debugUrl, HttpMethod.GET, null, JSON_OBJECT);

            // Still an unchecked cast: "data" is itself a nested JSON object, and a
            // Map<String, Object>'s values are erased to Object at runtime, so there's
            // no way for the compiler to verify this one level down. Safe here because
            // Facebook's debug_token response shape is fixed by their API contract.
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

            if (data == null || !(Boolean) data.get("is_valid")) {
                throw new CustomException("Invalid Facebook token");
            }

            String userId = (String) data.get("user_id");
            String userInfoUrl = "https://graph.facebook.com/" + userId +
                    "?fields=id,name,email&access_token=" + request.getToken();

            ResponseEntity<Map<String, Object>> userResponse =
                    restTemplate.exchange(userInfoUrl, HttpMethod.GET, null, JSON_OBJECT);
            Map<String, Object> userInfo = userResponse.getBody();

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");

            User user = findOrCreateSocialUser(email, name != null ? name : "Facebook User");
            user.setVerified(true);
            userRepository.save(user);

            return buildAuthResponse(user);
        } catch (Exception e) {
            throw new CustomException("Facebook login failed: " + e.getMessage());
        }
    }

    /**
     * GitHub sign up / sign in.
     * <p>
     * Unlike Google/Facebook, GitHub doesn't hand a mobile/web client a ready-made ID or
     * access token. The client instead drives the standard OAuth "web application flow": it
     * opens {@code https://github.com/login/oauth/authorize?client_id=...} in a
     * browser/webview, the user approves, and GitHub redirects back with a short-lived,
     * single-use {@code code}. That {@code code} is what the client sends here as
     * {@link SocialLoginRequest#getToken()} - this method does the server-side half of the
     * exchange (code -> access token -> profile), since the client secret must never be
     * shipped to the client.
     */
    public AuthResponse githubLogin(SocialLoginRequest request) {
        try {
            String accessToken = exchangeGithubCodeForToken(request.getToken());

            HttpHeaders profileHeaders = new HttpHeaders();
            profileHeaders.setBearerAuth(accessToken);
            profileHeaders.set(HttpHeaders.ACCEPT, "application/vnd.github+json");

            ResponseEntity<Map<String, Object>> profileResponse = restTemplate.exchange(
                    "https://api.github.com/user", HttpMethod.GET,
                    new HttpEntity<>(profileHeaders), JSON_OBJECT);

            Map<String, Object> profile = profileResponse.getBody();
            if (profile == null) {
                throw new CustomException("Failed to fetch GitHub profile");
            }

            String name = (String) profile.get("name");
            String login = (String) profile.get("login");
            String email = (String) profile.get("email");

            // GitHub only includes "email" on /user when the user has made it public.
            // Otherwise we fall back to /user/emails for their primary verified address.
            if (email == null) {
                email = fetchPrimaryGithubEmail(profileHeaders);
            }

            if (email == null) {
                throw new CustomException("GitHub account has no verified email address available");
            }

            User user = findOrCreateSocialUser(email, name != null ? name : login);
            user.setVerified(true);
            userRepository.save(user);

            return buildAuthResponse(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("GitHub login failed: " + e.getMessage());
        }
    }

    private String exchangeGithubCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = Map.of(
                "client_id", githubClientId,
                "client_secret", githubClientSecret,
                "code", code
        );

        ResponseEntity<Map<String, Object>> tokenResponse = restTemplate.exchange(
                "https://github.com/login/oauth/access_token", HttpMethod.POST,
                new HttpEntity<>(body, headers), JSON_OBJECT);

        Map<String, Object> tokenBody = tokenResponse.getBody();
        String accessToken = tokenBody != null ? (String) tokenBody.get("access_token") : null;

        if (accessToken == null) {
            String error = tokenBody != null
                    ? String.valueOf(tokenBody.getOrDefault("error_description", tokenBody.get("error")))
                    : "empty response";
            throw new CustomException("GitHub token exchange failed: " + error);
        }

        return accessToken;
    }

    private String fetchPrimaryGithubEmail(HttpHeaders headers) {
        ResponseEntity<List<Map<String, Object>>> emailsResponse = restTemplate.exchange(
                "https://api.github.com/user/emails", HttpMethod.GET,
                new HttpEntity<>(headers), JSON_ARRAY);

        List<Map<String, Object>> emails = emailsResponse.getBody();
        if (emails == null) {
            return null;
        }

        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                .map(e -> (String) e.get("email"))
                .findFirst()
                .orElse(null);
    }

    private User findOrCreateSocialUser(String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);

                    String[] nameParts = name.split(" ");
                    newUser.setFirstName(nameParts[0]);

                    if (nameParts.length > 1) {
                        newUser.setLastName(nameParts[nameParts.length - 1]);
                    }

                    newUser.setRole(Role.CLIENT);
                    return userRepository.save(newUser);
                });
    }

    private AuthResponse buildAuthResponse(User user) {
        JwtUtil.GeneratedToken generated = jwtUtil.generateToken(user);
        return new AuthResponse(generated.token(), user.getRole().name(), generated.expiresAt());
    }
}