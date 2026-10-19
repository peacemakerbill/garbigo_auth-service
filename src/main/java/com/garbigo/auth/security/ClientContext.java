package com.garbigo.auth.security;

import com.garbigo.auth.util.ClientIpResolver;

public record ClientContext(String ip, String userAgent, String deviceToken) {

    public static final ClientContext EMPTY = new ClientContext(ClientIpResolver.UNKNOWN, null, null);
}
