package com.garbigo.auth.security;

import com.garbigo.auth.util.SecureTokens;

import java.time.Duration;

public final class LoginThrottle {

    public record Config(int maxPairFailures, int maxIpFailures, int maxAccountFailures,
                         Duration window, Duration baseLockout, Duration maxLockout) {
    }

    public record Status(boolean locked, Duration retryAfter) {
        public static final Status OPEN = new Status(false, Duration.ZERO);
    }

    public enum Scope {
        NONE,
        PAIR,
        IP,
        ACCOUNT
    }

    public record FailureResult(boolean justLocked, Duration lockedFor, Scope scope) {
        public static final FailureResult NOT_LOCKED = new FailureResult(false, Duration.ZERO, Scope.NONE);
    }

    private static final Duration LOCK_COUNT_MEMORY = Duration.ofHours(24);
    private static final int MAX_DOUBLINGS = 10;

    private final AttemptStore store;
    private final Config config;

    public LoginThrottle(AttemptStore store, Config config) {
        this.store = store;
        this.config = config;
    }

    public Status check(String emailKey, String ip) {
        String account = accountId(emailKey);
        Duration longest = Duration.ZERO;
        for (String lockKey : new String[] {
                "login:lock:pair:" + account + ":" + ip,
                "login:lock:ip:" + ip,
                "login:lock:acct:" + account}) {
            Duration remaining = store.lockRemaining(lockKey);
            if (remaining != null && remaining.compareTo(longest) > 0) {
                longest = remaining;
            }
        }
        return longest.isZero() ? Status.OPEN : new Status(true, longest);
    }

    public FailureResult recordFailure(String emailKey, String ip) {
        String account = accountId(emailKey);
        FailureResult result = FailureResult.NOT_LOCKED;

        FailureResult pair = bump("pair:" + account + ":" + ip, config.maxPairFailures(), Scope.PAIR);
        FailureResult byIp = bump("ip:" + ip, config.maxIpFailures(), Scope.IP);
        FailureResult acct = bump("acct:" + account, config.maxAccountFailures(), Scope.ACCOUNT);

        for (FailureResult candidate : new FailureResult[] {pair, acct, byIp}) {
            if (candidate.justLocked() && (!result.justLocked()
                    || candidate.lockedFor().compareTo(result.lockedFor()) > 0)) {
                result = candidate;
            }
        }
        return result;
    }

    public void recordSuccess(String emailKey, String ip) {
        String account = accountId(emailKey);
        store.delete("login:fail:pair:" + account + ":" + ip);
        store.delete("login:lockcount:pair:" + account + ":" + ip);
    }

    private FailureResult bump(String scopeKey, int max, Scope scope) {
        long failures = store.increment("login:fail:" + scopeKey, config.window());
        if (failures < max) {
            return FailureResult.NOT_LOCKED;
        }

        Duration existing = store.lockRemaining("login:lock:" + scopeKey);
        if (existing != null && !existing.isZero()) {
            return FailureResult.NOT_LOCKED;
        }

        long lockCount = store.increment("login:lockcount:" + scopeKey, LOCK_COUNT_MEMORY);
        int doublings = (int) Math.min(Math.max(lockCount - 1, 0), MAX_DOUBLINGS);
        Duration lockFor = config.baseLockout().multipliedBy(1L << doublings);
        if (lockFor.compareTo(config.maxLockout()) > 0) {
            lockFor = config.maxLockout();
        }

        store.lock("login:lock:" + scopeKey, lockFor);
        store.delete("login:fail:" + scopeKey);
        return new FailureResult(true, lockFor, scope);
    }

    private static String accountId(String emailKey) {
        String key = emailKey == null ? "" : emailKey;
        return SecureTokens.sha256Hex(key).substring(0, 32);
    }
}
