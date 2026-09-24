package com.garbigo.auth.security;

import com.garbigo.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Reads back the userId claim set by generateToken. Returns null for a token
     * issued before this claim existed.
     */
    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    /**
     * Reads back the jti (JWT ID) claim set by generateToken - what
     * TokenBlacklistService keys revocation on. Returns null for a token issued
     * before this claim existed, which TokenBlacklistService treats as
     * "can't be revoked" rather than an error.
     */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /**
     * Public (rather than the previous private Date-returning version) so
     * TokenBlacklistService can compute how long a revocation entry needs to live:
     * exactly until the token would have expired on its own anyway.
     */
    public Instant extractExpiration(String token) {
        return extractClaim(token, claims -> claims.getExpiration().toInstant());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())  // Now correctly typed as SecretKey
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);  // Returns SecretKey directly
    }

    /**
     * A freshly issued token plus the exact instant it expires at. Both come from the
     * same Instant computed once in generateToken, so this expiresAt is guaranteed to
     * match the token's own "exp" claim exactly - callers can surface it to clients
     * (e.g. in AuthResponse) without re-parsing the token to find out.
     */
    public record GeneratedToken(String token, Instant expiresAt) {}

    public GeneratedToken generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiration);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())  // jti - what logout revokes by
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSignInKey())
                .compact();

        return new GeneratedToken(token, expiresAt);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).isBefore(Instant.now());
    }
}