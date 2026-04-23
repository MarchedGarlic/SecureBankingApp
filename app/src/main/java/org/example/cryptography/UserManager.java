package org.example.cryptography;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.example.db.DatabaseManager;

public class UserManager {
    /**
     * Uses DatabaseManager so every DB connection applies secure file permissions.
     */

    /**
     * This will create the initial table to hold users
     * @return
     * @throws EncryptionError
     */
    private static void initTables() throws EncryptionError {
        // Create the users table if it doesn't exist
        try (Connection conn = DatabaseManager.getConnection(); Statement stat = conn.createStatement()){
            stat.executeUpdate("CREATE TABLE IF NOT EXISTS users (id STRING PRIMARY KEY, username STRING NOT NULL UNIQUE, passwordHash STRING NOT NULL, salt STRING NOT NULL);");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EncryptionError("Error initializing user tables");
        }
    }

    /**
     * This will create a new user and save it to the disk. It will throw an error if the username is already taken
     * @param username
     * @param password
     * @throws EncryptionError
     */
    public static User newUser(String username, String password) throws EncryptionError {
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");

        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) {
            throw new EncryptionError("Username cannot be empty");
        }

        // Generate a random salt
        String salt = Encryption.generateSalt();

        // Generate the password hash
        String passwordHash = new String(Encryption.generateKeyBytes(password, salt));

        // Create a new user object with a random ID
        User user = new User(UUID.randomUUID().toString(), username.trim(), passwordHash, salt);

        // Save the user to disk
        return saveUser(user);
    }

    /**
     * This will delete a user from the database and all their notes from the disk
     * @param username
     * @throws EncryptionError
     */
    public static void deleteUser(String username) throws EncryptionError {
        Objects.requireNonNull(username, "Username cannot be null");

        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) {
            throw new EncryptionError("Username cannot be empty");
        }

        // This helps solve CWE-178 by matching usernames case-insensitively on delete.
        try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement("DELETE FROM users WHERE lower(username)=?;")){
            pstmt.setString(1, normalizedUsername);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EncryptionError("Error deleting user");
        }
    }

    /**
     * This will have a single user's data to the table and all their notes to files
     * @param user
     * @throws EncryptionError
     */
    public static User saveUser(User user) throws EncryptionError {
        Objects.requireNonNull(user, "User cannot be null");
        if (normalizeUsername(user.getUsername()).isBlank()) {
            throw new EncryptionError("Username cannot be empty");
        }

        try {
            // Make sure the tables actually exist
            initTables();

            // Insert the user into the table if they arent already inserted
            String sql = """
                INSERT INTO users(id, username, passwordHash, salt)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(id)
                DO UPDATE SET username=excluded.username, passwordHash=excluded.passwordHash, salt=excluded.salt;
            """;
            try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, user.getId());
                pstmt.setString(2, user.getUsername());
                pstmt.setString(3, user.getPasswordHash());
                pstmt.setString(4, user.getSalt());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            // If there's a SQL error, throw a user exception saying the save couldn't be completed
            e.printStackTrace();
            throw new EncryptionError("Error saving user");
        }

        return user;
    }

    /**
     * This function will take a username and password and use it to load the data of the user
     * @param username
     * @param password
     * @return
     * @throws EncryptionError
     */
    public static User loadUser(String username, String password) throws EncryptionError {
        Objects.requireNonNull(username, "Username cannot be null");
        Objects.requireNonNull(password, "Password cannot be null");

        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername.isBlank()) {
            throw new EncryptionError("Username cannot be empty");
        }

        try {
            // Make sure tables exist, even if just to query something that doesn't exist
            initTables();

            // Retrieve the user fields
            // This helps solve CWE-178 by matching usernames with normalized case.
            String sql = "SELECT * FROM users WHERE lower(username)=?;";

            try (Connection conn = DatabaseManager.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, normalizedUsername);

                ResultSet rs = pstmt.executeQuery();

                // Get results and instantiate new user object from it
                if(!rs.next())
                    throw new EncryptionError("User not found");

                String userID = rs.getString("id");
                String canonicalUsername = rs.getString("username");
                String passwordHashFromDB = rs.getString("passwordHash");
                String salt = rs.getString("salt");

                // Compare the password hash to their supplied password, which solves CWE-328: Use of Weak Hash.
                // This is because it compares the two strong keys instead of a weak hash like SHA-1
                String passwordHashFromArgs = new String(Encryption.generateKeyBytes(password, salt));

                if(!passwordHashFromArgs.equals(passwordHashFromDB))
                    throw new EncryptionError("Invalid password");

                // Create the actual user — store the hash, never the plaintext password
                User user = new User(userID, canonicalUsername, passwordHashFromDB, salt);

                // Return the resulting construction
                return user;
            }
        } catch(SQLException e){
            e.printStackTrace();
            throw new EncryptionError("Error loading user");
        }
    }

    /**
     * This function will load every user from the disk and return it as a list. It is a debug function because it will bypass passwords
     * @return
     * @throws EncryptionError 
     */
    private static ArrayList<String> getAllUsernames() throws EncryptionError {
        // Prep a location to keep the users
        ArrayList<String> usernames = new ArrayList<>();

        try {
            // Make sure there is SOMETHING to query
            initTables();
            
            // Query for all the users
            try (Connection conn = DatabaseManager.getConnection(); Statement stat = conn.createStatement()){
                ResultSet rs = stat.executeQuery("SELECT username FROM users");
    
                // Load each user
                while (rs.next()) {
                    usernames.add(rs.getString("username"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EncryptionError("Error loading users");
        }

        return usernames;
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public static void main(String[] args) throws EncryptionError {
        User user1 = UserManager.newUser("Test", "Password");
        User user2 = UserManager.newUser("John", "abc123");
        User user3 = UserManager.newUser("Doe", "hello");

        saveUser(user1);
        saveUser(user2);
        saveUser(user3);

        System.out.println(getAllUsernames());

        try {
            System.out.println(loadUser("John", "abc123"));
            System.out.println(loadUser("John", "abc1223"));
        } catch (EncryptionError e) {
            System.out.println(e.getMessage());
        }

        deleteUser("Test");
        deleteUser("John");
        deleteUser("Doe");
    }
}
