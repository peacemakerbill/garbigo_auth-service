package com.garbigo.auth.security;

import java.time.Instant;

public final class SessionPolicy {

    public enum Verdict {
        ALLOWED,
        REVOKED,
        EXPIRED,
        UNTRACKED_REJECTED,
        CUTOFF_REJECTED
    }

    private SessionPolicy() {
    }

    public static Verdict evaluate(boolean tracked, Instant revokedAt, Instant expiresAt, boolean allowUntracked,
                                   long issuedAtEpochSecond, Long tokensValidAfterEpochSecond, Instant now) {
        if (tracked) {
            if (revokedAt != null) {
                return Verdict.REVOKED;
            }
            if (expiresAt != null && !expiresAt.isAfter(now)) {
                return Verdict.EXPIRED;
            }
            return Verdict.ALLOWED;
        }

        if (!allowUntracked) {
            return Verdict.UNTRACKED_REJECTED;
        }
        if (tokensValidAfterEpochSecond != null && issuedAtEpochSecond < tokensValidAfterEpochSecond) {
            return Verdict.CUTOFF_REJECTED;
        }
        return Verdict.ALLOWED;
    }
}
