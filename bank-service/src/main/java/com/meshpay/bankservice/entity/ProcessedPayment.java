package com.meshpay.bankservice.entity;

import jakarta.persistence.*;
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
@Entity
@Table(name = "processed_payments")
public class ProcessedPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "BINARY(16)")
    private UUID transactionId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column(nullable = false)
    private String checksum;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String receiver;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal senderBalance;

    @Column(nullable = false)
    private BigDecimal receiverBalance;
}
