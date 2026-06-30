package com.meshpay.common.crypto;

import com.meshpay.common.dto.EncryptedPayload;
import com.meshpay.common.hash.HashService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class HybridEncryptionService {

    private static final String ALGORITHM_DESCR = "AES-256/GCM/NoPadding & RSA-2048/ECB/OAEPWithSHA-256AndMGF1Padding";

    public static EncryptedPayload encrypt(String plainText, PublicKey rsaPublicKey) {
        if (plainText == null || rsaPublicKey == null) {
            throw new IllegalArgumentException("Plain text and RSA public key must not be null");
        }
        try {
            // 1. Generate AES key & IV
            SecretKey aesKey = AesUtility.generateKey();
            byte[] iv = AesUtility.generateIv();

            // 2. Hash plaintext before encryption
            String checksum = HashService.generateHash(plainText);

            // 3. Encrypt data using AES
            byte[] encryptedDataBytes = AesUtility.encrypt(plainText.getBytes(StandardCharsets.UTF_8), aesKey, iv);

            // 4. Encrypt AES key using RSA public key
            byte[] encryptedKeyBytes = RsaUtility.encrypt(aesKey.getEncoded(), rsaPublicKey);

            // 5. Package into DTO
            return EncryptedPayload.builder()
                    .encryptedData(Base64.getEncoder().encodeToString(encryptedDataBytes))
                    .encryptedKey(Base64.getEncoder().encodeToString(encryptedKeyBytes))
                    .iv(Base64.getEncoder().encodeToString(iv))
                    .checksum(checksum)
                    .algorithm(ALGORITHM_DESCR)
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Hybrid encryption failed", e);
        }
    }

    public static String decrypt(EncryptedPayload payload, PrivateKey rsaPrivateKey) {
        if (payload == null || rsaPrivateKey == null) {
            throw new IllegalArgumentException("Payload and RSA private key must not be null");
        }
        try {
            // 1. Decode fields from Base64
            byte[] encryptedKeyBytes = Base64.getDecoder().decode(payload.getEncryptedKey());
            byte[] encryptedDataBytes = Base64.getDecoder().decode(payload.getEncryptedData());
            byte[] iv = Base64.getDecoder().decode(payload.getIv());

            // 2. Decrypt AES key using RSA private key
            byte[] aesKeyBytes = RsaUtility.decrypt(encryptedKeyBytes, rsaPrivateKey);
            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            // 3. Decrypt text using AES key
            byte[] decryptedBytes = AesUtility.decrypt(encryptedDataBytes, aesKey, iv);
            String decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);

            // 4. Verify checksum
            if (!HashService.verifyHash(decryptedText, payload.getChecksum())) {
                throw new SecurityException("Integrity check failed: Decrypted payload checksum does not match original");
            }

            return decryptedText;
        } catch (Exception e) {
            throw new RuntimeException("Hybrid decryption failed", e);
        }
    }
}
