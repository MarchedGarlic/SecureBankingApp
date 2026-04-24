package org.example.cryptography;

import java.util.Arrays;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Encryption {
    private static final int ITERATIONS = 100000;
    private static final int KEY_LENGTH = 512;

    // [CWE-1241] A single shared SecureRandom instance is initialised once at class-load
    // time. SecureRandom seeds itself from a cryptographically strong OS entropy source
    // (e.g. /dev/urandom on Linux, CryptGenRandom on Windows). Re-creating a
    // new SecureRandom() on every call risks reusing the same seed across close
    // invocations on some JVMs, making salt values predictable. Using a shared instance
    // ensures the generator advances its internal state between calls, keeping
    // all generated values unpredictable to an attacker.
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    /**
     * Generates a key byte array from a password and salt using PBKDF2 with HMAC SHA-256.
     * @param password
     * @param salt
     * @return
     * @throws EncryptionError
     */
    public static byte[] generateKeyBytes(String password, String salt) throws EncryptionError {
        // This solves the CWE-261: Weak Encoding for Password vulnerability by using
        // a strong key derivation instead, making it hard to brute force the password hash
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), ITERATIONS, KEY_LENGTH);
            byte[] derivedKey = f.generateSecret(spec).getEncoded();
            // This helps solve CWE-466 by returning only bytes from a valid array range.
            return Arrays.copyOfRange(derivedKey, 0, derivedKey.length);
        } catch (Exception e) {
            throw new EncryptionError("Error generating key bytes");
        }
    }

    /**
     * Generates a random salt string using the shared SECURE_RANDOM instance.
     * Uses the shared SECURE_RANDOM instance declared above.
     * @return hex-encoded random salt
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    private Encryption(){}
}
