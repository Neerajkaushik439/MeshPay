package com.meshpay.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class AesUtility {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE_BYTES = 12;
    private static final int TAG_BIT_LENGTH = 128;

    private static final SecureRandom secureRandom = new SecureRandom();

    public static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, secureRandom);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    public static byte[] generateIv() {
        byte[] iv = new byte[IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static byte[] encrypt(byte[] plainText, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec);
            return cipher.doFinal(plainText);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    public static byte[] decrypt(byte[] cipherText, SecretKey key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_BIT_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec);
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}
