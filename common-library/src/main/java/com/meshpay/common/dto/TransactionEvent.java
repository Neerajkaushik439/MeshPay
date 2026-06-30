package com.meshpay.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private UUID transactionId;
    private LocalDateTime timestamp;
    private String serviceName;
    private String currentStage; // CREATED, ENCRYPTED, RELAY_1, RELAY_2, GATEWAY, BANK, COMPLETED
    private String transactionStatus; // CREATED, ENCRYPTED, ROUTING, SUCCESS, FAILED, EXPIRED
    private String packetStatus; // RECEIVED, FORWARDED, DROPPED, FAILED, DELIVERED_TO_GATEWAY
    private int hopCount;
    private List<String> routeHistory;
    private String message;
}
