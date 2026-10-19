package com.garbigo.auth.security;

import com.garbigo.auth.config.SecurityProperties;
import com.garbigo.auth.util.ClientIpResolver;
import com.garbigo.auth.util.SecureTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Component
public class ClientContextFactory {

    public static final String DEVICE_TOKEN_HEADER = "X-Device-Token";

    private final ClientIpResolver ipResolver;
    private final SecurityProperties properties;

    public ClientContextFactory(ClientIpResolver ipResolver, SecurityProperties properties) {
        this.ipResolver = ipResolver;
        this.properties = properties;
    }

    public ClientContext from(HttpServletRequest request) {
        if (request == null) {
            return ClientContext.EMPTY;
        }
        String ip = ipResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return new ClientContext(ip, userAgent, readDeviceToken(request));
    }

    public ClientContext current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return from(attributes.getRequest());
        }
        return ClientContext.EMPTY;
    }

    public String resolveIp(HttpServletRequest request) {
        return ipResolver.resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
    }

    public void attachDeviceToken(HttpServletResponse response, String token) {
        if (token == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(properties.getDeviceCookieName(), token)
                .httpOnly(true)
                .secure(properties.isDeviceCookieSecure())
                .sameSite(properties.getDeviceCookieSameSite())
                .path("/")
                .maxAge(Duration.ofDays(properties.getDeviceCookieDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setHeader(DEVICE_TOKEN_HEADER, token);
    }

    private String readDeviceToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (properties.getDeviceCookieName().equals(cookie.getName())
                        && SecureTokens.looksLikeToken(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        String header = request.getHeader(DEVICE_TOKEN_HEADER);
        return SecureTokens.looksLikeToken(header) ? header : null;
    }
}
