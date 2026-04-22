package org.example.authentication;

import org.example.cryptography.EncryptionError;
import org.example.cryptography.User;
import org.example.cryptography.UserManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoginService bridges UserManager (credential verification) and AuthService
 * (session token management) into a single, banking-grade login flow.
 *
 * Features:
 *   - Brute-force protection via per-username lockout after N failed attempts
 *   - Configurable session lengths (SHORT = 5 min, STANDARD = 30 min, EXTENDED = 8 h)
 *   - Idle-timeout refresh: every valid API call can extend the session
 *   - Hard-limit: a session can never live beyond MAX_SESSION_HOURS regardless of activity
 *   - logout() and logoutAll() for explicit session teardown
 *   - pruneExpiredSessions() for periodic DB housekeeping
 */
public class LoginService {

    // -------------------------------------------------------------------------
    // Session length presets (banking apps typically offer short / standard)
    // -------------------------------------------------------------------------
    public enum SessionLength {
        /** 5-minute session - used for high-sensitivity operations or demo keys */
        SHORT(5, ChronoUnit.MINUTES),

        /** 30-minute idle-timeout session - typical online banking default */
        STANDARD(30, ChronoUnit.MINUTES),

        /** 8-hour session - suitable for internal staff / back-office portals */
        EXTENDED(8, ChronoUnit.HOURS);

        final long amount;
        final ChronoUnit unit;

        SessionLength(long amount, ChronoUnit unit) {
            this.amount = amount;
            this.unit = unit;
        }

        public Instant expiryFrom(Instant now) {
            return now.plus(amount, unit);
        }
    }

    private static final long MAX_SESSION_HOURS    = 24;
    private static final int  MAX_FAILED_ATTEMPTS  = 5;
    private static final long LOCKOUT_DURATION_MINS = 15;

    private static final String DB_ADAPTER = "jdbc:sqlite:bank.db";

    // In-memory failed-attempt counter (survives only for the JVM lifetime;
    // for a multi-node deployment, move this to a shared cache like Redis)
    private static final Map<String, FailedAttemptRecord> failedAttempts =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Authenticate a user and open a new session.
     *
     * @param username      supplied username
     * @param password      supplied plain-text password
     * @param sessionLength desired session duration
     * @return              a valid {@link Session} containing the bearer token and expiry
     * @throws AuthenticationError on bad credentials, locked account, or DB error
     */
    public static Session login(String username, String password,
                                SessionLength sessionLength) throws AuthenticationError {
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");
        Objects.requireNonNull(sessionLength, "SessionLength cannot be null");

        // Check lockout before touching the DB (fail fast, avoid timing oracle)
        checkLockout(username);

        // Verify credentials via UserManager
        User user;
        try {
            user = UserManager.loadUser(username, password);
        } catch (EncryptionError e) {
            recordFailedAttempt(username);
            // Use a generic message to avoid username enumeration (CWE-204)
            throw new AuthenticationError("Invalid username or password");
        }

        // Credentials good - reset any lingering failed-attempt counter
        failedAttempts.remove(username);

        Instant now       = Instant.now();
        Instant expiry    = sessionLength.expiryFrom(now);
        Instant hardLimit = now.plus(MAX_SESSION_HOURS, ChronoUnit.HOURS);
        if (expiry.isAfter(hardLimit)) expiry = hardLimit;

        try {
            initTables();
            // [CWE-488] Session is bound to user.getId() - the identity verified against the DB -
            // not to the raw username string supplied by the caller. This ensures one user cannot
            // craft a token that references another user's data.
            Session session = SessionStore.create(user.getId(), username, now, expiry, hardLimit);
            return session;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error creating session");
        }
    }

    /**
     * Convenience overload that uses {@link SessionLength#STANDARD} (30 minutes).
     */
    public static Session login(String username, String password) throws AuthenticationError {
        return login(username, password, SessionLength.STANDARD);
    }

    /**
     * Validate an existing session token. If the session is still within its
     * idle window, the expiry is slid forward by the original session duration
     * (up to the hard-limit ceiling).
     *
     * @param token         the bearer token returned at login
     * @param sessionLength the session's original duration (used for idle refresh)
     * @return              the refreshed {@link Session}
     * @throws AuthenticationError if the token is absent, expired, or revoked
     */
    public static Session validateAndRefresh(String token,
                                             SessionLength sessionLength) throws AuthenticationError {
        Objects.requireNonNull(token, "Token cannot be null");

        try {
            initTables();
            Session session = SessionStore.findByToken(token);

            if (session == null) {
                throw new AuthenticationError("Session not found");
            }
            // [CWE-613] Expired sessions are hard-deleted from the DB immediately rather than
            // just rejected in memory. This ensures the token cannot be replayed after expiry
            // even if the expiry check were somehow bypassed on a future call.
            if (session.isExpired()) {
                SessionStore.delete(token);
                throw new AuthenticationError("Session has expired - please log in again");
            }

            // Slide the idle expiry forward, but never past the hard limit
            Instant newExpiry = sessionLength.expiryFrom(Instant.now());
            if (newExpiry.isAfter(session.getHardLimit())) {
                newExpiry = session.getHardLimit();
            }
            SessionStore.updateExpiry(token, newExpiry);
            return session.withExpiry(newExpiry);

        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error validating session");
        }
    }

    /**
     * Validates without refreshing - use this for read-only status checks.
     */
    public static Session validate(String token) throws AuthenticationError {
        Objects.requireNonNull(token, "Token cannot be null");

        try {
            initTables();
            Session session = SessionStore.findByToken(token);

            if (session == null || session.isExpired()) {
                if (session != null) SessionStore.delete(token);
                throw new AuthenticationError("Session is invalid or expired");
            }
            return session;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error validating session");
        }
    }

    /**
     * Explicitly log out by deleting the session token from the DB.
     *
     * @param token the bearer token to invalidate
     */
    public static void logout(String token) throws AuthenticationError {
        Objects.requireNonNull(token, "Token cannot be null");
        try {
            initTables();
            SessionStore.delete(token);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error logging out");
        }
    }

    /**
     * Terminate every active session for a user (e.g. after password change or
     * suspicious activity detection).
     *
     * @param userId the user's UUID
     */
    public static void logoutAll(String userId) throws AuthenticationError {
        Objects.requireNonNull(userId, "UserId cannot be null");
        try {
            initTables();
            SessionStore.deleteAllForUser(userId);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error logging out all sessions");
        }
    }

    /**
     * Housekeeping: delete all sessions whose expiry timestamp is in the past.
     * Call this on a scheduler (e.g. once per hour).
     */
    public static void pruneExpiredSessions() throws AuthenticationError {
        try {
            initTables();
            SessionStore.pruneExpired();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error pruning expired sessions");
        }
    }

    // -------------------------------------------------------------------------
    // Lockout helpers
    // -------------------------------------------------------------------------

    private static void checkLockout(String username) throws AuthenticationError {
        FailedAttemptRecord record = failedAttempts.get(username);
        if (record == null) return;

        if (record.attempts >= MAX_FAILED_ATTEMPTS) {
            Instant unlockTime = record.firstFailure.plus(LOCKOUT_DURATION_MINS, ChronoUnit.MINUTES);
            if (Instant.now().isBefore(unlockTime)) {
                long secsLeft = Instant.now().until(unlockTime, ChronoUnit.SECONDS);
                throw new AuthenticationError(
                        "Account temporarily locked. Try again in " + secsLeft + " seconds.");
            } else {
                failedAttempts.remove(username);
            }
        }
    }

    private static void recordFailedAttempt(String username) {
        failedAttempts.compute(username, (k, existing) -> {
            if (existing == null) return new FailedAttemptRecord(1, Instant.now());
            return new FailedAttemptRecord(existing.attempts + 1, existing.firstFailure);
        });
    }

    private record FailedAttemptRecord(int attempts, Instant firstFailure) {}

    // -------------------------------------------------------------------------
    // DB init
    // -------------------------------------------------------------------------

    // [CWE-250] This connection is opened solely to run CREATE TABLE IF NOT EXISTS and is
    // closed immediately by try-with-resources. No elevated or DDL-capable connection is
    // held open during normal read/write operations, following the principle of least privilege.
    private static void initTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             Statement stat = conn.createStatement()) {
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sessions (
                    token       TEXT PRIMARY KEY,
                    user_id     TEXT NOT NULL,
                    username    TEXT NOT NULL,
                    created_at  DATETIME NOT NULL,
                    expires_at  DATETIME NOT NULL,
                    hard_limit  DATETIME NOT NULL
                );
            """);
        }
    }

    // -------------------------------------------------------------------------
    // Inner helper: raw DB operations isolated here for clarity
    // -------------------------------------------------------------------------

    private static final class SessionStore {

        static Session create(String userId, String username,
                              Instant createdAt, Instant expiresAt,
                              Instant hardLimit) throws SQLException {
            java.security.SecureRandom rng = new java.security.SecureRandom();
            byte[] bytes = new byte[32];
            rng.nextBytes(bytes);
            String token = java.util.HexFormat.of().formatHex(bytes);

            try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO sessions(token, user_id, username, created_at, expires_at, hard_limit) " +
                         "VALUES(?,?,?,?,?,?);")) {
                ps.setString(1, token);
                ps.setString(2, userId);
                ps.setString(3, username);
                ps.setTimestamp(4, java.sql.Timestamp.from(createdAt));
                ps.setTimestamp(5, java.sql.Timestamp.from(expiresAt));
                ps.setTimestamp(6, java.sql.Timestamp.from(hardLimit));
                ps.executeUpdate();
            }
            return new Session(token, userId, username, createdAt, expiresAt, hardLimit);
        }

        static Session findByToken(String token) throws SQLException {
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM sessions WHERE token=?;")) {
                ps.setString(1, token);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) return null;
                return new Session(
                        rs.getString("token"),
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("hard_limit").toInstant()
                );
            }
        }

        static void updateExpiry(String token, Instant newExpiry) throws SQLException {
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE sessions SET expires_at=? WHERE token=?;")) {
                ps.setTimestamp(1, java.sql.Timestamp.from(newExpiry));
                ps.setString(2, token);
                ps.executeUpdate();
            }
        }

        static void delete(String token) throws SQLException {
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM sessions WHERE token=?;")) {
                ps.setString(1, token);
                ps.executeUpdate();
            }
        }

        static void deleteAllForUser(String userId) throws SQLException {
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM sessions WHERE user_id=?;")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
        }

        static void pruneExpired() throws SQLException {
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM sessions WHERE expires_at <= ?;")) {
                ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        }

        private SessionStore() {}
    }

    private LoginService() {}
}
