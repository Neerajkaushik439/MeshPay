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
public class Packet {
    private String packetId;
    private UUID transactionId;
    private String encryptedData;
    private String encryptedKey;
    private String iv;
    private String checksum;
    private LocalDateTime ttl;
    private int hopCount;
    private int maxHopCount;
    private String currentNode;
    private String nextNode;
    private LocalDateTime createdAt;
    private List<String> routeHistory;
    private PacketStatus packetStatus;
}
