package com.example.reporting.tracking;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers for stored PDFs (AD-07): digests and lower-case hex, as stored in {@code SHA256}. */
public final class Sha256 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha256() {
    }

    /**
     * Returns a new SHA-256 digest, for hashing a PDF while it is written.
     *
     * @return digest
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }

    /**
     * Returns the lower-case hex SHA-256 of the bytes.
     *
     * @param bytes content
     * @return 64 hex characters
     */
    public static String hex(byte[] bytes) {
        return toHex(newDigest().digest(bytes));
    }

    /**
     * Encodes a digest as lower-case hex.
     *
     * @param digest digest bytes
     * @return hex text
     */
    public static String toHex(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return hex.toString();
    }
}
