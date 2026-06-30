package com.meshpay.transactionservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID transactionId;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false)
    private String senderEmail;

    @Column(nullable = false)
    private String senderUpiId;

    @Column(nullable = false)
    private String receiverUpiId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false)
    private LocalDateTime ttl;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean encrypted;

    private String relayNode;

    private String gatewayNode;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String checksum;

    @Column(nullable = false)
    private String packetVersion;

    // Encrypted Hybrid Payload variables
    @Lob
    @Column(columnDefinition = "TEXT")
    private String encryptedData;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String encryptedKey;

    private String iv;
}
