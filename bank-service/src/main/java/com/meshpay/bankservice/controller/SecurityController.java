package com.meshpay.bankservice.controller;

import com.meshpay.common.crypto.HybridEncryptionService;
import com.meshpay.common.dto.EncryptedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.KeyPair;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
@Slf4j
public class SecurityController {

    private final KeyPair bankKeyPair;

    @GetMapping("/public-key")
    public ResponseEntity<String> getPublicKey() {
        byte[] publicKeyBytes = bankKeyPair.getPublic().getEncoded();
        String base64PublicKey = Base64.getEncoder().encodeToString(publicKeyBytes);
        return ResponseEntity.ok(base64PublicKey);
    }

    @PostMapping("/test-encrypt")
    public ResponseEntity<Map<String, Object>> testEncrypt(@RequestBody String plainJson) {
        log.info("Encryption started: Running hybrid encryption test...");
        
        // 1. Encrypt payload
        EncryptedPayload encryptedPayload = HybridEncryptionService.encrypt(plainJson, bankKeyPair.getPublic());
        log.info("Encryption completed: Hybrid encryption completed.");

        // 2. Decrypt payload
        String decryptedJson = HybridEncryptionService.decrypt(encryptedPayload, bankKeyPair.getPrivate());
        log.info("Verification completed: Hybrid decryption and checksum verification completed.");

        // 3. Verify status
        boolean verificationStatus = plainJson.equals(decryptedJson);

        Map<String, Object> response = new HashMap<>();
        response.put("originalJson", plainJson);
        response.put("encryptedPayload", encryptedPayload);
        response.put("decryptedJson", decryptedJson);
        response.put("verificationStatus", verificationStatus);

        return ResponseEntity.ok(response);
    }
}
