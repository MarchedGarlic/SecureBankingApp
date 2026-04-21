package org.example.authentication;

import java.time.Instant;

public class AuthKey {
    private final String key;
    private final Instant createdAt;
    private final Instant expiresAt;

    public AuthKey(String key, Instant createdAt, Instant expiresAt) {
        this.key = key;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getValue() {
        return key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isExpired();
    }
}
