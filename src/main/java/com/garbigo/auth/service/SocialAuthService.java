package com.garbigo.auth.service;

import com.garbigo.auth.dto.SignInResult;
import com.garbigo.auth.enums.SignInMethod;
import com.garbigo.auth.model.User;
import com.garbigo.auth.security.ClientContext;
import com.garbigo.auth.util.EmailAddresses;
import com.garbigo.auth.dto.SocialLoginRequest;
import com.garbigo.auth.exception.CustomException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);

    private final SocialAccountLinker linker;
    private final AuthService authService;
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

    public SocialAuthService(SocialAccountLinker linker, AuthService authService) {
        this.linker = linker;
        this.authService = authService;
    }

    public SignInResult googleLogin(SocialLoginRequest request, ClientContext ctx) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getToken());
            if (idToken == null) {
                log.warn("Google sign-in: the ID token failed verification (bad signature, expired, wrong token "
                        + "type, or the audience does not match GOOGLE_CLIENT_ID)");
                throw new CustomException("We couldn't verify your Google account. Please try signing in again.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = EmailAddresses.normalize(payload.getEmail());
            if (email == null) {
                throw new CustomException("Your Google account didn't share an email address. "
                        + "Please sign up with email instead.");
            }
            String name = (String) payload.get("name");
            boolean confirmed = Boolean.TRUE.equals(payload.getEmailVerified());

            return complete(new SocialAccountLinker.Identity(email, name != null ? name : "Google User", null,
                    confirmed, SignInMethod.GOOGLE), ctx);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google sign-in failed", e);
            throw new CustomException("We couldn't sign you in with Google. Please try again.");
        }
    }

    public SignInResult facebookLogin(SocialLoginRequest request, ClientContext ctx) {
        try {
            String appAccessToken = facebookAppId + "|" + facebookAppSecret;
            String debugUrl = "https://graph.facebook.com/debug_token?input_token="
                    + request.getToken() + "&access_token=" + appAccessToken;

            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(debugUrl, HttpMethod.GET, null, JSON_OBJECT);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

            if (data == null || !Boolean.TRUE.equals(data.get("is_valid"))) {
                throw new CustomException("We couldn't verify your Facebook account. Please try signing in again.");
            }

            if (!facebookAppId.trim().equals(String.valueOf(data.get("app_id")))) {
                log.warn("Facebook sign-in: the token was issued for a different app");
                throw new CustomException("We couldn't verify your Facebook account. Please try signing in again.");
            }

            String userId = (String) data.get("user_id");
            String userInfoUrl = "https://graph.facebook.com/" + userId +
                    "?fields=id,name,email&access_token=" + request.getToken();

            ResponseEntity<Map<String, Object>> userResponse =
                    restTemplate.exchange(userInfoUrl, HttpMethod.GET, null, JSON_OBJECT);
            Map<String, Object> userInfo = userResponse.getBody();

            String email = EmailAddresses.normalize((String) userInfo.get("email"));
            String name = (String) userInfo.get("name");

            if (email == null) {
                throw new CustomException("Your Facebook account didn't share an email address. Please add "
                        + "an email to your Facebook account and allow email access, or sign up with email instead.");
            }

            return complete(new SocialAccountLinker.Identity(email, name != null ? name : "Facebook User", null,
                    false, SignInMethod.FACEBOOK), ctx);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Facebook sign-in failed", e);
            throw new CustomException("We couldn't sign you in with Facebook. Please try again.");
        }
    }

    public SignInResult githubLogin(SocialLoginRequest request, ClientContext ctx) {
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

            String email = fetchPrimaryGithubEmail(profileHeaders);
            boolean confirmed = email != null;
            if (email == null) {
                email = (String) profile.get("email");
            }
            email = EmailAddresses.normalize(email);

            if (email == null) {
                throw new CustomException("Your GitHub account needs a verified email address before you can "
                        + "sign in. Please verify an email on GitHub and try again.");
            }

            return complete(new SocialAccountLinker.Identity(email, name != null ? name : login, login,
                    confirmed, SignInMethod.GITHUB), ctx);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub sign-in failed", e);
            throw new CustomException("We couldn't sign you in with GitHub. Please try again.");
        }
    }

    private SignInResult complete(SocialAccountLinker.Identity identity, ClientContext ctx) {
        User user = linker.resolveAndPromote(identity);
        return authService.finishSignIn(user, identity.provider(), ctx);
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
            log.warn("GitHub sign-in: the code exchange did not return an access token");
            throw new CustomException("We couldn't sign you in with GitHub. Please try again.");
        }

        return accessToken;
    }

    private String fetchPrimaryGithubEmail(HttpHeaders headers) {
        try {
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
        } catch (Exception e) {
            log.debug("Could not read the GitHub email list: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
