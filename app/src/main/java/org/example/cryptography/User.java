package org.example.cryptography;

public class User {
    private final String id;
    private final String username;
    private final String passwordHash;
    private final String salt;

    public User(String id, String username, String passwordHash, String salt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }
}