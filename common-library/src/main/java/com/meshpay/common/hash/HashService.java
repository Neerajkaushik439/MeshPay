package com.meshpay.common.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class HashService {

    public static String generateHash(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public static boolean verifyHash(String payload, String checksum) {
        if (payload == null || checksum == null) {
            return false;
        }
        String computed = generateHash(payload);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                checksum.getBytes(StandardCharsets.UTF_8)
        );
    }
}
