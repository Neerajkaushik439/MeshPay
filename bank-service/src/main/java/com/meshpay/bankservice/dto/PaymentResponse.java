package com.meshpay.bankservice.dto;

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
public class PaymentResponse {
    private UUID transactionId;
    private String status;
    private String message;
    private LocalDateTime processedAt;
    private BigDecimal senderBalance;
    private BigDecimal receiverBalance;
}
