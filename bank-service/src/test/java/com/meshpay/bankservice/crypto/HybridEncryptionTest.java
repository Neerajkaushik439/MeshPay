package com.meshpay.bankservice.crypto;

import com.meshpay.common.crypto.AesUtility;
import com.meshpay.common.crypto.HybridEncryptionService;
import com.meshpay.common.crypto.RsaUtility;
import com.meshpay.common.dto.EncryptedPayload;
import com.meshpay.common.hash.HashService;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

public class HybridEncryptionTest {

    @Test
    public void testAesEncryptionDecryption() {
        SecretKey key = AesUtility.generateKey();
        byte[] iv = AesUtility.generateIv();
        String originalText = "Hello AES-256 GCM Cryptography!";

        byte[] cipherText = AesUtility.encrypt(originalText.getBytes(StandardCharsets.UTF_8), key, iv);
        assertNotNull(cipherText);
        assertTrue(cipherText.length > 0);

        byte[] decryptedBytes = AesUtility.decrypt(cipherText, key, iv);
        String decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);

        assertEquals(originalText, decryptedText);
    }

    @Test
    public void testRsaEncryptionDecryption() {
        KeyPair keyPair = RsaUtility.generateKeyPair();
        String originalSecret = "SecureAESKeyBytes-Generated";

        byte[] cipherText = RsaUtility.encrypt(originalSecret.getBytes(StandardCharsets.UTF_8), keyPair.getPublic());
        assertNotNull(cipherText);
        assertTrue(cipherText.length > 0);

        byte[] decryptedBytes = RsaUtility.decrypt(cipherText, keyPair.getPrivate());
        String decryptedSecret = new String(decryptedBytes, StandardCharsets.UTF_8);

        assertEquals(originalSecret, decryptedSecret);
    }

    @Test
    public void testShaHashAndVerify() {
        String data = "ImportantTransactionDataPayload";
        String checksum = HashService.generateHash(data);

        assertNotNull(checksum);
        assertTrue(HashService.verifyHash(data, checksum));
        assertFalse(HashService.verifyHash(data + "Modified", checksum));
    }

    @Test
    public void testHybridEncryptionRoundTrip() {
        KeyPair keyPair = RsaUtility.generateKeyPair();
        String originalJson = "{\"transactionId\":\"txn-12345\",\"amount\":1500.75,\"sender\":\"alice@mesh\",\"receiver\":\"bob@mesh\"}";

        // Encrypt with RSA public key
        EncryptedPayload payload = HybridEncryptionService.encrypt(originalJson, keyPair.getPublic());
        assertNotNull(payload);
        assertNotNull(payload.getEncryptedData());
        assertNotNull(payload.getEncryptedKey());
        assertNotNull(payload.getIv());
        assertNotNull(payload.getChecksum());
        assertEquals("AES-256/GCM/NoPadding & RSA-2048/ECB/OAEPWithSHA-256AndMGF1Padding", payload.getAlgorithm());

        // Decrypt with RSA private key
        String decryptedJson = HybridEncryptionService.decrypt(payload, keyPair.getPrivate());
        assertEquals(originalJson, decryptedJson);
    }
}
