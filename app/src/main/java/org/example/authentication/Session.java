package org.example.authentication;

import java.time.Instant;

/**
 * Immutable value object representing an active user session.
 *
 * Callers receive a {@code Session} from {@link LoginService#login} and pass
 * the contained {@link #getToken()} as a bearer credential on subsequent
 * requests. The token should be treated as a secret and transmitted only
 * over TLS.
 */
public final class Session {

    private final String  token;
    private final String  userId;
    private final String  username;
    private final Instant createdAt;
    private final Instant expiresAt;  // idle-timeout expiry - slides on activity
    private final Instant hardLimit;  // absolute ceiling - never slides

    public Session(String token, String userId, String username,
                   Instant createdAt, Instant expiresAt, Instant hardLimit) {
        this.token     = token;
        this.userId    = userId;
        this.username  = username;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.hardLimit = hardLimit;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The 256-bit hex-encoded bearer token. */
    public String getToken()      { return token; }

    /** UUID of the authenticated user. */
    public String getUserId()     { return userId; }

    /** Human-readable username. */
    public String getUsername()   { return username; }

    /** When this session was first opened. */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Current idle-timeout expiry. Moves forward each time
     * {@link LoginService#validateAndRefresh} is called (up to the hard limit).
     */
    public Instant getExpiresAt() { return expiresAt; }

    /**
     * Absolute maximum lifetime of this session. Not extendable regardless of
     * activity. The user must re-authenticate once this is reached.
     */
    public Instant getHardLimit() { return hardLimit; }

    // -------------------------------------------------------------------------
    // Convenience predicates
    // -------------------------------------------------------------------------

    /** {@code true} if the idle-timeout expiry has passed. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** {@code true} if the hard-limit ceiling has been reached. */
    public boolean isHardExpired() {
        return Instant.now().isAfter(hardLimit);
    }

    /** {@code true} if the session is still usable. */
    public boolean isValid() {
        return !isExpired() && !isHardExpired();
    }

    // -------------------------------------------------------------------------
    // Package-level helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a copy of this session with an updated {@code expiresAt}.
     * All other fields - including userId and hardLimit - are carried over
     * unchanged, so a refresh cannot alter the session's identity or ceiling.
     */
    Session withExpiry(Instant newExpiry) {
        return new Session(token, userId, username, createdAt, newExpiry, hardLimit);
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        // Token is deliberately omitted to prevent it appearing in logs or stack traces
        return "Session{userId='" + userId + "', username='" + username +
               "', expiresAt=" + expiresAt + ", hardLimit=" + hardLimit + "}";
    }
}
