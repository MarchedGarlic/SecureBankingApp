package org.example.authentication;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

public class AuthService {
    /**
     * Generates a new authentication key with a short expiration time.
     * @return
     */
    public static AuthKey generateKey() {
        // Generate random token by creating a list of random bytes and encoding it as a hex string
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        Instant now = Instant.now();
        Instant expiry = now.plus(5, ChronoUnit.MINUTES);

        return new AuthKey(token, now, expiry);
    }

    /**
     * Validates an authentication key by checking if it has expired.
     * @param key
     */
    public void validate(AuthKey key) {
        if(key.isExpired()){
            throw new SecurityException("Key has expired");
        }
    }

    private AuthService() {}
}
