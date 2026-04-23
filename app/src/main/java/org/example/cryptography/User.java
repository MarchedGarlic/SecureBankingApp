package org.example.cryptography;

import java.util.Objects;

public class User {
    private final String id;
    private final String username;
    private final String passwordHash;
    private final String salt;

    public User(String id, String username, String passwordHash, String salt) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash cannot be null");
        this.salt = Objects.requireNonNull(salt, "Salt cannot be null");
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