package com.garbigo.auth.security;

import com.garbigo.auth.dto.MessageResponse;
import com.garbigo.auth.model.User;
import com.garbigo.auth.service.SessionService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

@Component
public class JwtFilter extends OncePerRequestFilter {

    public static final String SESSION_JTI_ATTRIBUTE = "com.garbigo.auth.sessionJti";

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SessionService sessionService;
    private final TokenReplayMonitor replayMonitor;
    private final ClientContextFactory contexts;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public JwtFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService,
                      TokenBlacklistService tokenBlacklistService, SessionService sessionService,
                      TokenReplayMonitor replayMonitor, ClientContextFactory contexts) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.sessionService = sessionService;
        this.replayMonitor = replayMonitor;
        this.contexts = contexts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                String jti = jwtUtil.extractJti(token);

                if (tokenBlacklistService.isRevoked(jti)) {
                    replayMonitor.report(jti, jwtUtil.extractUserId(token), contexts.resolveIp(request));
                    respondUnauthorized(response, "You have been signed out. Please sign in again.");
                    return;
                }

                String email = jwtUtil.extractUsername(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (jwtUtil.validateToken(token, userDetails)) {
                        if (!userDetails.isEnabled() || !(userDetails instanceof User user)) {
                            respondUnauthorized(response, "Your session is no longer valid. Please sign in again.");
                            return;
                        }

                        Instant issuedAt = jwtUtil.extractIssuedAt(token);
                        SessionService.TokenCheck check = sessionService.check(jti, user, issuedAt);
                        if (!check.allowed()) {
                            if (check.verdict() == SessionPolicy.Verdict.REVOKED) {
                                replayMonitor.report(jti, user.getId(), contexts.resolveIp(request));
                            }
                            respondUnauthorized(response, messageFor(check.verdict()));
                            return;
                        }
                        sessionService.touch(check.session());
                        request.setAttribute(SESSION_JTI_ATTRIBUTE, jti);

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, token, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (ExpiredJwtException e) {
                respondUnauthorized(response, "Your session has expired. Please sign in again.");
                return;
            } catch (JwtException | UsernameNotFoundException e) {
                log.debug("JWT authentication failed: {}", e.getClass().getSimpleName());
                respondUnauthorized(response, "Your session is no longer valid. Please sign in again.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String messageFor(SessionPolicy.Verdict verdict) {
        return switch (verdict) {
            case REVOKED -> "You have been signed out. Please sign in again.";
            case EXPIRED -> "Your session has expired. Please sign in again.";
            default -> "Your session is no longer valid. Please sign in again.";
        };
    }

    private void respondUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(new MessageResponse(message)));
    }
}
