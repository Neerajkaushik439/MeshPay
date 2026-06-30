package com.meshpay.transactionservice.dto;

import com.meshpay.transactionservice.entity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private UUID transactionId;
    private Long senderUserId;
    private String senderEmail;
    private String senderUpiId;
    private String receiverUpiId;
    private BigDecimal amount;
    private TransactionStatus status;
    private LocalDateTime ttl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean encrypted;
    private String relayNode;
    private String gatewayNode;
    private int retryCount;
    private String idempotencyKey;
    private String checksum;
    private String packetVersion;

    // Encrypted payloads
    private String encryptedData;
    private String encryptedKey;
    private String iv;
}
