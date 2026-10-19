package com.garbigo.auth.security;

import java.time.Duration;

public interface AttemptStore {

    long increment(String key, Duration ttlIfNew);

    void lock(String key, Duration ttl);

    Duration lockRemaining(String key);

    void delete(String key);
}
