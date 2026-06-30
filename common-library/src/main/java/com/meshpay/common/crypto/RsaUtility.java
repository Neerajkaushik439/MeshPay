package com.meshpay.common.crypto;

import javax.crypto.Cipher;
import java.security.*;

public class RsaUtility {

    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int KEY_SIZE = 2048;

    private static final SecureRandom secureRandom = new SecureRandom();

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyGen.initialize(KEY_SIZE, secureRandom);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    public static byte[] encrypt(byte[] plainText, PublicKey key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(plainText);
        } catch (Exception e) {
            throw new RuntimeException("RSA encryption failed", e);
        }
    }

    public static byte[] decrypt(byte[] cipherText, PrivateKey key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("RSA decryption failed", e);
        }
    }
}
