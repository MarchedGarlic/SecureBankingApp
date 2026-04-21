package org.example.authentication;

import java.time.Instant;

public class AuthKey {
    private final String token;
    private final Instant createdAt;
    private final Instant expiresAt;

    public AuthKey(String token, Instant createdAt, Instant expiresAt) {
        this.token = token;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
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
