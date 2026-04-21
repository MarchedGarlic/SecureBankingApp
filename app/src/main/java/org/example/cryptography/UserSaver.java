package org.example.cryptography;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Objects;

// Test user class example
class User {
    private static int nextID = 0;

    private String id;
    private String username;
    private String password;
    private String salt;

    public User(String username, String password) {
        this.id = "user_" + ++nextID;
        this.username = username;
        this.password = password;
        this.salt = Encryption.generateSalt();
    }

    User(String id, String username, String password, String salt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.salt = salt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getSalt() {
        return salt;
    }
}

// Actual user saving logic
public class UserSaver {
    private static final String DB_ADAPTER = "jdbc:sqlite:bank.db";

    /**
     * This will create the initial table to hold users
     * @return
     * @throws EncryptionError
     */
    private static void initTables() throws EncryptionError {
        // Create the users table if it doesn't exist
        try (Connection conn = DriverManager.getConnection(DB_ADAPTER); Statement stat = conn.createStatement()){
            stat.executeUpdate("CREATE TABLE IF NOT EXISTS users (id STRING PRIMARY KEY, username STRING NOT NULL UNIQUE, passwordHash STRING NOT NULL, salt STRING NOT NULL);");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new EncryptionError("Error initializing user tables");
        }
    }

    /**
     * This will have a single user's data to the table and all their notes to files
     * @param user
     * @throws EncryptionError
     */
    public static void saveUser(User user) throws EncryptionError {
        Objects.requireNonNull(user, "User cannot be null");

        try {
            // Make sure the tables actually exist
            initTables();

            // Convert user password to hash
            String passwordHash = new String(Encryption.generateKeyBytes(user.getPassword(), user.getSalt()));

            // Insert the user into the table if they arent already inserted
            String sql = """
                INSERT INTO users(id, username, passwordHash, salt)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(id)
                DO UPDATE SET username=excluded.username, passwordHash=excluded.passwordHash, salt=excluded.salt;
            """;
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER); PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, user.getId());
                pstmt.setString(2, user.getUsername());
                pstmt.setString(3, passwordHash);
                pstmt.setString(4, user.getSalt());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            // If there's a SQL error, throw a user exception saying the save couldn't be completed
            e.printStackTrace();
            throw new EncryptionError("Error saving user");
        }
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

        try {
            // Make sure tables exist, even if just to query something that doesn't exist
            initTables();

            // Retrieve the user fields
            String sql = "SELECT * FROM users WHERE username=?;";

            try (Connection conn = DriverManager.getConnection(DB_ADAPTER); PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, username);

                ResultSet rs = pstmt.executeQuery();

                // Get results and instantiate new user object from it
                if(!rs.next())
                    throw new EncryptionError("User not found");

                String userID = rs.getString("id");
                String passwordHashFromDB = rs.getString("passwordHash");
                String salt = rs.getString("salt");

                // Compare the password hash to their supplied password
                String passwordHashFromArgs = new String(Encryption.generateKeyBytes(password, salt));

                if(!passwordHashFromArgs.equals(passwordHashFromDB))
                    throw new EncryptionError("Invalid password");

                // Create the actual user
                User user = new User(userID, username, password, salt);

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
            try (Connection conn = DriverManager.getConnection(DB_ADAPTER); Statement stat = conn.createStatement()){
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

    public static void main(String[] args) throws EncryptionError {
        User user1 = new User("Test", "Password");
        User user2 = new User("John", "abc123");
        User user3 = new User("Doe", "hello");

        saveUser(user1);
        saveUser(user2);
        saveUser(user3);

        System.out.println(getAllUsernames());
        System.out.println(loadUser("John", "abc123"));
        System.out.println(loadUser("John", "abc1223"));
    }
}
