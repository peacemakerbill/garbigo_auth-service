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

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

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
                throw new CustomException("We couldn't verify your Google account. Please try signing in again.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            User user = findOrCreateSocialUser(email, name != null ? name : "Google User");
            user.setVerified(true);
            userRepository.save(user);

            return buildAuthResponse(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("We couldn't sign you in with Google. Please try again.");
        }
    }

    public AuthResponse facebookLogin(SocialLoginRequest request) {
        try {
            String appAccessToken = facebookAppId + "|" + facebookAppSecret;
            String debugUrl = "https://graph.facebook.com/debug_token?input_token="
                    + request.getToken() + "&access_token=" + appAccessToken;

            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(debugUrl, HttpMethod.GET, null, JSON_OBJECT);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

            if (data == null || !(Boolean) data.get("is_valid")) {
                throw new CustomException("We couldn't verify your Facebook account. Please try signing in again.");
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
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("We couldn't sign you in with Facebook. Please try again.");
        }
    }

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
                throw new CustomException("We couldn't retrieve your GitHub profile. Please try again.");
            }

            String name = (String) profile.get("name");
            String login = (String) profile.get("login");
            String email = (String) profile.get("email");

            if (email == null) {
                email = fetchPrimaryGithubEmail(profileHeaders);
            }

            if (email == null) {
                throw new CustomException("Your GitHub account needs a verified email address before you can "
                        + "sign in. Please verify an email on GitHub and try again.");
            }

            User user = findOrCreateSocialUser(email, name != null ? name : login);
            user.setVerified(true);
            userRepository.save(user);

            return buildAuthResponse(user);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException("We couldn't sign you in with GitHub. Please try again.");
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
            throw new CustomException("We couldn't sign you in with GitHub. Please try again.");
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