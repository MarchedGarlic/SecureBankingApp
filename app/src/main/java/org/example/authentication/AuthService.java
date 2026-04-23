package org.example.authentication;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

import org.example.db.DatabaseManager;

public class AuthService {
    /**
     * Uses DatabaseManager so every DB connection applies secure file permissions.
     */

    /**
     * This will create the initial table to hold sessions
     * @return
     * @throws AuthenticationError
     */
    private static void initTables() throws AuthenticationError {
        // Create the sessions table if it doesn't exist
        try (Connection conn = DatabaseManager.getConnection(); Statement stat = conn.createStatement()){
            stat.executeUpdate("CREATE TABLE IF NOT EXISTS sessions (token STRING PRIMARY KEY, created_at DATETIME, expires_at DATETIME);");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error initializing session tables");
        }
    }
    
    /**
     * Generates a new authentication key with a short expiration time.
     * @return
     */
    public static AuthKey generateKey() throws AuthenticationError {
        // Generate random token by creating a list of random bytes and encoding it as a hex string
        // This solves  CWE-331: Insufficient Entropy vulnerability by using a secure random generator
        // guaranteeing this wont create clusters
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32]; // CWE-334: Small Space of Random Values is solved here by using 32 bytes, which gives us 256 bits of entropy, making it infeasible to brute force the token
        secureRandom.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        Instant now = Instant.now();
        Instant expiry = now.plus(5, ChronoUnit.MINUTES);

        // Insert the token into the database
        initTables();

        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement stat = conn.prepareStatement("INSERT INTO sessions (token, created_at, expires_at) VALUES (?, ?, ?);")){
            stat.setString(1, token);
            stat.setTimestamp(2, java.sql.Timestamp.from(now));
            stat.setTimestamp(3, java.sql.Timestamp.from(expiry));
            stat.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error saving authentication key");
        }

        return new AuthKey(token, now, expiry);
    }

    /**
     * Validates an authentication key by checking if it has expired.
     * @param boolean
     * @throws AuthenticationError 
     */
    public static boolean validateKey(AuthKey key) throws AuthenticationError {
        Objects.requireNonNull(key, "Key cannot be null");
        // This helps solve  CWE-324: Use of a Key Past its Expiration Date vulnerability by checking if the key
        // has expired before validating preventing the use of expired keys
        if(key.isExpired()){
            throw new SecurityException("Key has expired");
        }

        // Check if the token exists in the database and check if its expired time is in the future
        initTables();
        
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement stat = conn.prepareStatement("SELECT * FROM sessions WHERE token=? AND expires_at > ?;")){
            stat.setString(1, key.getToken());
            stat.setTimestamp(2, java.sql.Timestamp.from(Instant.now()));
            return stat.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new SecurityException("Error validating authentication key");
        }
    }

    /**
     * Invalidates an authentication key by removing it from the database.
     * @param key
     * @throws AuthenticationError
     */
    public static void invalidateKey(AuthKey key) throws AuthenticationError {
        Objects.requireNonNull(key, "Key cannot be null");
        // Remove the token from the database
        initTables();

        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement stat = conn.prepareStatement("DELETE FROM sessions WHERE token=?;")){
            stat.setString(1, key.getToken());
            stat.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error invalidating authentication key");
        }
    }

    /**
     * This will remove all expired keys from the database. This should be called periodically to prevent the database from filling up with expired keys.
     * @throws AuthenticationError
     */
    public static void pruneExpiredKeys() throws AuthenticationError {
        // Remove all expired tokens from the database
        initTables();

        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement stat = conn.prepareStatement("DELETE FROM sessions WHERE expires_at <= ?;")){
            stat.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
            stat.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new AuthenticationError("Error pruning expired authentication keys");
        }
    }

    private AuthService() {}

    public static void main(String[] args) throws AuthenticationError {
        AuthKey key = generateKey();
        System.out.println("Generated key: " + key.getToken());
        System.out.println("Is valid: " + validateKey(key));
        invalidateKey(key);
        System.out.println("Is valid after invalidation: " + validateKey(key));
    }
}
