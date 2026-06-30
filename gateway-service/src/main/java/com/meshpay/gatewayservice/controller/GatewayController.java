package com.meshpay.gatewayservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshpay.common.dto.Packet;
import com.meshpay.common.dto.PacketStatus;
import com.meshpay.common.dto.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/gateway")
@Slf4j
public class GatewayController {

    private final RestTemplate restTemplate;

    public GatewayController() {
        // Configure RestTemplate with JavaTimeModule for proper LocalDateTime serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        this.restTemplate.getMessageConverters().add(0, converter);
        
        System.out.println("========================================");
        System.out.println("[GATEWAY] RestTemplate initialized with JavaTimeModule");
        System.out.println("========================================");
    }

    @Value("${services.bank-service.url}")
    private String bankServiceUrl;

    @Value("${services.transaction-service.url}")
    private String transactionServiceUrl;

    @PostMapping("/receive")
    public ResponseEntity<Packet> receivePacket(@RequestBody Packet packet) {
        System.out.println("========================================");
        System.out.println("[GATEWAY] STEP 0: /api/gateway/receive ENDPOINT HIT");
        System.out.println("[GATEWAY] Packet ID: " + (packet != null ? packet.getPacketId() : "NULL"));
        System.out.println("[GATEWAY] Transaction ID: " + (packet != null ? packet.getTransactionId() : "NULL"));
        System.out.println("[GATEWAY] Packet Status: " + (packet != null ? packet.getPacketStatus() : "NULL"));
        System.out.println("[GATEWAY] Hop Count: " + (packet != null ? packet.getHopCount() : "NULL"));
        System.out.println("[GATEWAY] Max Hop Count: " + (packet != null ? packet.getMaxHopCount() : "NULL"));
        System.out.println("[GATEWAY] TTL: " + (packet != null ? packet.getTtl() : "NULL"));
        System.out.println("[GATEWAY] Current Node: " + (packet != null ? packet.getCurrentNode() : "NULL"));
        System.out.println("[GATEWAY] Next Node: " + (packet != null ? packet.getNextNode() : "NULL"));
        System.out.println("[GATEWAY] Route History: " + (packet != null ? packet.getRouteHistory() : "NULL"));
        System.out.println("[GATEWAY] Has Checksum: " + (packet != null && packet.getChecksum() != null));
        System.out.println("========================================");

        log.info("Gateway received packet: Ingested packet ID {}", packet != null ? packet.getPacketId() : "null");

        // 1. Validate packet properties
        System.out.println("[GATEWAY] STEP 1: Validating packet properties...");
        if (packet == null || packet.getChecksum() == null || packet.getChecksum().isEmpty()) {
            System.out.println("[GATEWAY] STEP 1 FAILED: Packet or checksum missing!");
            log.warn("Forward failed: Packet or checksum missing.");
            if (packet != null) {
                packet.setPacketStatus(PacketStatus.FAILED);
                notifyTransactionService(packet);
                publishEvent(packet, "GATEWAY", "FAILED", "Gateway Drop: Packet details or checksum missing");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(packet);
        }
        System.out.println("[GATEWAY] STEP 1 PASSED: Packet and checksum valid");

        // 2. Validate TTL
        System.out.println("[GATEWAY] STEP 2: Validating TTL...");
        if (packet.getTtl() != null && LocalDateTime.now().isAfter(packet.getTtl())) {
            System.out.println("[GATEWAY] STEP 2 FAILED: TTL expired! TTL=" + packet.getTtl() + " NOW=" + LocalDateTime.now());
            log.warn("Forward failed: Packet TTL expired for packet ID {}", packet.getPacketId());
            packet.setPacketStatus(PacketStatus.DROPPED);
            notifyTransactionService(packet);
            publishEvent(packet, "GATEWAY", "FAILED", "Gateway Drop: TTL expired");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(packet);
        }
        System.out.println("[GATEWAY] STEP 2 PASSED: TTL is valid");

        // 3. Validate Hop Count limits
        System.out.println("[GATEWAY] STEP 3: Validating Hop Count... hopCount=" + packet.getHopCount() + " maxHopCount=" + packet.getMaxHopCount());
        if (packet.getHopCount() >= packet.getMaxHopCount()) {
            System.out.println("[GATEWAY] STEP 3 FAILED: Hop count exceeded!");
            log.warn("Forward failed: Packet exceeded maximum hop count limits for packet ID {}", packet.getPacketId());
            packet.setPacketStatus(PacketStatus.DROPPED);
            notifyTransactionService(packet);
            publishEvent(packet, "GATEWAY", "FAILED", "Gateway Drop: Max hops exceeded");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(packet);
        }
        System.out.println("[GATEWAY] STEP 3 PASSED: Hop count within limits");

        // 4. Update Packet route history & state
        System.out.println("[GATEWAY] STEP 4: Updating packet metadata...");
        packet.setHopCount(packet.getHopCount() + 1);
        if (packet.getRouteHistory() == null) {
            packet.setRouteHistory(new ArrayList<>());
        }
        packet.getRouteHistory().add("Gateway");
        packet.setCurrentNode("Gateway");
        packet.setNextNode("Bank");
        packet.setPacketStatus(PacketStatus.RECEIVED);
        System.out.println("[GATEWAY] STEP 4 DONE: hopCount=" + packet.getHopCount() + " routeHistory=" + packet.getRouteHistory());

        // 5. Notify Transaction Service (reached gateway)
        System.out.println("[GATEWAY] STEP 5: Notifying transaction-service...");
        notifyTransactionService(packet);
        publishEvent(packet, "GATEWAY", "ROUTING", "Gateway Received: Ingested packet from mesh");
        System.out.println("[GATEWAY] STEP 5 DONE");

        // 6. Forward packet directly to Bank Service with 3 retries (2s delay)
        int attempt = 0;
        String targetUrl = bankServiceUrl + "/api/payments/process";
        System.out.println("[GATEWAY] STEP 6: Forwarding packet to Bank at URL: " + targetUrl);

        log.info("Forwarding started: Sending packet ID {} to Bank", packet.getPacketId());

        while (attempt < 3) {
            try {
                if (attempt > 0) {
                    System.out.println("[GATEWAY] STEP 6 RETRY: Attempt " + (attempt + 1) + " to forward to Bank");
                    publishEvent(packet, "GATEWAY", "ROUTING", "Retry Started: Attempt " + (attempt + 1) + " to forward packet to Bank");
                }
                System.out.println("[GATEWAY] STEP 6." + (attempt + 1) + ": Sending POST to " + targetUrl);
                System.out.println("[GATEWAY] STEP 6." + (attempt + 1) + ": About to call restTemplate.postForEntity...");
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<Packet> response = restTemplate.postForEntity(targetUrl, packet, Packet.class);
                long elapsed = System.currentTimeMillis() - startTime;
                
                System.out.println("[GATEWAY] STEP 6." + (attempt + 1) + ": Response received in " + elapsed + "ms");
                System.out.println("[GATEWAY] STEP 6." + (attempt + 1) + ": Response Status Code: " + response.getStatusCode());
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    System.out.println("[GATEWAY] STEP 6 SUCCESS: Packet forwarded to Bank!");
                    log.info("Forward successful: Packet ID {} routed to Bank", packet.getPacketId());
                    packet.setPacketStatus(PacketStatus.FORWARDED);
                    notifyTransactionService(packet);
                    publishEvent(packet, "GATEWAY", "ROUTING", "Gateway Forwarded: Packet forwarded to Bank successfully");
                    return ResponseEntity.ok(packet);
                }
                System.out.println("[GATEWAY] STEP 6 ERROR: Non-2xx response: " + response.getStatusCode());
                throw new RuntimeException("Bank Service returned status: " + response.getStatusCode());
            } catch (Exception e) {
                attempt++;
                System.out.println("[GATEWAY] STEP 6 EXCEPTION on attempt " + attempt + ": " + e.getClass().getName() + " - " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("[GATEWAY] STEP 6 ROOT CAUSE: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
                }
                log.warn("Retry attempt {}: Failed to send packet ID {} to Bank. Error: {}", attempt, packet.getPacketId(), e.getMessage());
                if (attempt >= 3) {
                    System.out.println("[GATEWAY] STEP 6 FINAL FAILURE: All 3 retries exhausted!");
                    log.error("Forward failed: Exhausted all retries. Packet ID {} failed to route to Bank", packet.getPacketId());
                    packet.setPacketStatus(PacketStatus.FAILED);
                    notifyTransactionService(packet);
                    publishEvent(packet, "GATEWAY", "FAILED", "Gateway Forwarding Failed: Failed to route packet to Bank after all retries");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(packet);
                }
                System.out.println("[GATEWAY] STEP 6 WAITING: Sleeping 2s before retry...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }

        System.out.println("[GATEWAY] STEP 7 FAILURE: Fell through retry loop - marking FAILED");
        packet.setPacketStatus(PacketStatus.FAILED);
        notifyTransactionService(packet);
        publishEvent(packet, "GATEWAY", "FAILED", "Gateway Forwarding Failed: Routing failed");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(packet);
    }

    private void notifyTransactionService(Packet packet) {
        String callbackUrl = transactionServiceUrl + "/api/transactions/packet-status";
        System.out.println("[GATEWAY] NOTIFY: Sending packet-status to " + callbackUrl + " status=" + packet.getPacketStatus());
        try {
            restTemplate.postForEntity(callbackUrl, packet, Void.class);
            System.out.println("[GATEWAY] NOTIFY: Successfully notified transaction-service");
            log.info("Notification sent: Successfully notified Transaction Service for packet {} status {}", 
                    packet.getPacketId(), packet.getPacketStatus());
        } catch (Exception e) {
            System.out.println("[GATEWAY] NOTIFY FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            log.warn("Failed to notify Transaction Service for packet {} status {}. Error: {}", 
                    packet.getPacketId(), packet.getPacketStatus(), e.getMessage());
        }
    }

    private void publishEvent(Packet packet, String stage, String txnStatus, String message) {
        String eventUrl = transactionServiceUrl + "/api/transactions/events";
        System.out.println("[GATEWAY] EVENT: Publishing event to " + eventUrl);
        System.out.println("[GATEWAY] EVENT: Stage=" + stage + " TxnStatus=" + txnStatus + " Message=" + message);
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(packet.getTransactionId())
                    .timestamp(LocalDateTime.now())
                    .serviceName("Gateway")
                    .currentStage(stage)
                    .transactionStatus(txnStatus)
                    .packetStatus(packet.getPacketStatus() != null ? packet.getPacketStatus().name() : null)
                    .hopCount(packet.getHopCount())
                    .routeHistory(packet.getRouteHistory())
                    .message(message)
                    .build();
            restTemplate.postForEntity(eventUrl, event, Void.class);
            System.out.println("[GATEWAY] EVENT: Successfully published event");
        } catch (Exception e) {
            System.out.println("[GATEWAY] EVENT FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            log.warn("Failed to publish event to Transaction Service: {}", e.getMessage());
        }
    }
}
