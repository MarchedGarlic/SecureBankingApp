package org.example.authentication;

import java.time.Instant;
import java.util.Objects;

public class AuthKey {
    private final String token;
    private final Instant createdAt;
    private final Instant expiresAt;

    public AuthKey(String token, Instant createdAt, Instant expiresAt) {
        this.token = Objects.requireNonNull(token, "Token cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created at cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires at cannot be null");
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
