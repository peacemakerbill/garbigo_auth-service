package com.garbigo.auth.service;

import com.garbigo.auth.dto.AuthResponse;
import com.garbigo.auth.dto.SocialLoginRequest;
import com.garbigo.auth.dto.UserDto;
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
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SocialAuthService {

    // Shared, reusable descriptor for a plain JSON-object response body.
    // Using this with RestTemplate.exchange(...) instead of getForEntity(url, Map.class)
    // gets a properly generic Map<String, Object> back instead of a raw Map.
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ModelMapper modelMapper = new ModelMapper();

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

    public AuthResponse appleLogin(SocialLoginRequest request) {
        try {
            String jwksUrl = "https://appleid.apple.com/auth/keys";
            ResponseEntity<Map<String, Object>> jwksResponse =
                    restTemplate.exchange(jwksUrl, HttpMethod.GET, null, JSON_OBJECT);

            // Same story as "data" above: "keys" is a nested JSON array of objects inside
            // a Map<String, Object>, so extracting it is an inherently unchecked cast.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys =
                    (List<Map<String, Object>>) jwksResponse.getBody().get("keys");

            String[] parts = request.getToken().split("\\.");
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, String> header =
                    jsonMapper.readValue(headerJson, new TypeReference<Map<String, String>>() {});
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

            // Claims.getAudience() already returns Set<String> - no cast needed (and
            // casting a Set to a List, as the previous code did, was never actually valid).
            Set<String> audience = claims.getAudience();
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
        UserDto userDto = modelMapper.map(user, UserDto.class);

        return new AuthResponse(
                jwtUtil.generateToken(user),
                user.getRole().name(),
                user.isVerified(),
                userDto
        );
    }
}