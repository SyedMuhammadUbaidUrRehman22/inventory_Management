package com.retailpos.util;

import org.mindrot.jbcrypt.BCrypt;

/** BCrypt password hashing. Passwords are never stored in plain text. */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /** Returns a salted BCrypt hash of the given plain-text password. */
    public static String hash(String plain) {
        if (plain == null) {
            throw new IllegalArgumentException("Password must not be null");
        }
        return BCrypt.hashpw(plain, BCrypt.gensalt());
    }

    /**
     * Returns true if {@code plain} matches {@code hash}.
     * Returns false (instead of throwing) for null input or a malformed hash.
     */
    public static boolean verify(String plain, String hash) {
        if (plain == null || hash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plain, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
