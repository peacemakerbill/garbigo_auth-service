package com.garbigo.auth.security;

import com.garbigo.auth.dto.MessageResponse;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public JwtFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService,
                      TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // A revoked token would otherwise still pass signature + expiry checks
                // below just fine - that's the whole reason this check has to exist.
                // Unlike "no token sent" (which just proceeds unauthenticated and lets
                // Spring Security's normal rules decide), a token that WAS valid and has
                // been explicitly revoked gets a specific, actionable response instead of
                // falling through to a generic access-denied - the client sent a real
                // credential, it's just been logged out.
                if (tokenBlacklistService.isRevoked(jwtUtil.extractJti(token))) {
                    respondUnauthorized(response, "Token has been revoked. Please sign in again.");
                    return;
                }

                String email = jwtUtil.extractUsername(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (jwtUtil.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, token, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (ExpiredJwtException e) {
                respondUnauthorized(response, "Token has expired. Please sign in again.");
                return;
            } catch (JwtException | UsernameNotFoundException e) {
                // JwtException covers a malformed/tampered/wrong-signature token.
                // UsernameNotFoundException means the token's subject doesn't match any
                // existing account - e.g. the user was deleted, or this is a stale token
                // from before a database reset in dev. Either way it's an unusable
                // credential, not a server bug: this filter runs in the servlet chain,
                // outside Spring MVC entirely, so GlobalExceptionHandler's @ExceptionHandlers
                // can never see or catch this - without this try/catch, it was propagating
                // all the way past the filter chain as a raw, uncaught exception (a bare
                // Tomcat error page, not JSON, and a full stack trace dumped to the logs
                // for what is normally a routine, expected failure).
                System.err.println("JWT auth failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                respondUnauthorized(response, "Invalid token. Please sign in again.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void respondUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(new MessageResponse(message)));
    }
}