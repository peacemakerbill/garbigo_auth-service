package com.garbigo.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SocialAuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${facebook.app-id}")
    private String facebookAppId;

    @Value("${facebook.app-secret}")
    private String facebookAppSecret;

    @Value("${apple.client-id}")
    private String appleClientId;

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

            ResponseEntity<Map> response = restTemplate.getForEntity(debugUrl, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

            if (data == null || !(Boolean) data.get("is_valid")) {
                throw new CustomException("Invalid Facebook token");
            }

            String userId = (String) data.get("user_id");
            String userInfoUrl = "https://graph.facebook.com/" + userId +
                    "?fields=id,name,email&access_token=" + request.getToken();

            ResponseEntity<Map> userResponse = restTemplate.getForEntity(userInfoUrl, Map.class);
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

    public AuthResponse appleLogin(SocialLoginRequest request) {
        try {
            String jwksUrl = "https://appleid.apple.com/auth/keys";
            ResponseEntity<Map> jwksResponse = restTemplate.getForEntity(jwksUrl, Map.class);
            List<Map<String, Object>> keys =
                    (List<Map<String, Object>>) jwksResponse.getBody().get("keys");

            String[] parts = request.getToken().split("\\.");
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, String> header = objectMapper.readValue(headerJson, Map.class);
            String kid = header.get("kid");

            Map<String, Object> key = keys.stream()
                    .filter(k -> kid.equals(k.get("kid")))
                    .findFirst()
                    .orElseThrow(() -> new CustomException("Apple public key not found"));

            BigInteger modulus = new BigInteger(1,
                    Base64.getUrlDecoder().decode((String) key.get("n")));
            BigInteger exponent = new BigInteger(1,
                    Base64.getUrlDecoder().decode((String) key.get("e")));

            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(request.getToken())
                    .getPayload();

            List<String> audience = (List<String>) claims.getAudience();
            if (audience == null || !audience.contains(appleClientId)) {
                throw new CustomException("Invalid Apple audience");
            }

            String email = claims.get("email", String.class);
            String sub = claims.getSubject();

            String fallbackEmail = email != null ? email : "appleuser_" + sub;
            String name = "Apple User";

            User user = findOrCreateSocialUser(fallbackEmail, name);
            user.setVerified(true);
            userRepository.save(user);

            return buildAuthResponse(user);
        } catch (Exception e) {
            throw new CustomException("Apple login failed: " + e.getMessage());
        }
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
        AuthResponse response = new AuthResponse();
        response.setToken(jwtUtil.generateToken(user));
        response.setRole(user.getRole().name());

        // Internal logic instead of external service call
        response.setDashboardUrl(getDashboardUrl(user.getRole()));

        return response;
    }

    private String getDashboardUrl(Role role) {
        switch (role) {
            case ADMIN:
                return "/dashboard/admin";
            case COLLECTOR:
                return "/dashboard/collector";
            case CLIENT:
            default:
                return "/dashboard/client";
        }
    }
}