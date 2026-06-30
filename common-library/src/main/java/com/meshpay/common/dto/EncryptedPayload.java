package com.meshpay.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedPayload {
    private String encryptedData;  // Base64 encoded encrypted payload JSON
    private String encryptedKey;   // Base64 encoded encrypted AES key
    private String iv;             // Base64 encoded initialization vector
    private String checksum;       // SHA-256 hash of original plain text payload
    private String algorithm;      // Description of encryption algorithms used
    private long timestamp;        // Timestamp when payload was encrypted (epoch millis)
}
