package io.consenthub.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic lowercase hexadecimal SHA-256 hashing utility.
 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256Hex(String input) {
        return HexFormat.of().formatHex(sha256Bytes(input));
    }

    public static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }
}
