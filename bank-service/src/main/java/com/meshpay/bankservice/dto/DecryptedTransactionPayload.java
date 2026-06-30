package com.meshpay.bankservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecryptedTransactionPayload {
    private UUID transactionId;
    private String senderUpiId;
    private String receiverUpiId;
    private BigDecimal amount;
}
