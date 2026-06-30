package com.meshpay.transactionservice.dto;

import com.meshpay.transactionservice.entity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusResponse {
    private UUID transactionId;
    private TransactionStatus status;
    private LocalDateTime updatedAt;
}
