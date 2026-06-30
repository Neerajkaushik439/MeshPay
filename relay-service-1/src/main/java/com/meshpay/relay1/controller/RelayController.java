package com.meshpay.relay1.controller;

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
@RequestMapping("/api/relay")
@Slf4j
public class RelayController {

    private final RestTemplate restTemplate;

    public RelayController() {
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
        System.out.println("[RELAY-1] RestTemplate initialized with JavaTimeModule");
        System.out.println("========================================");
    }

    @Value("${services.next-node.url}")
    private String nextNodeUrl;

    @Value("${services.transaction-service.url:http://localhost:8086}")
    private String transactionServiceUrl;

    @PostMapping("/receive")
    public ResponseEntity<Packet> receivePacket(@RequestBody Packet packet) {
        System.out.println("========================================");
        System.out.println("[RELAY-1] STEP 0: /api/relay/receive ENDPOINT HIT");
        System.out.println("[RELAY-1] Packet ID: " + (packet != null ? packet.getPacketId() : "NULL"));
        System.out.println("[RELAY-1] Transaction ID: " + (packet != null ? packet.getTransactionId() : "NULL"));
        System.out.println("[RELAY-1] Packet Status: " + (packet != null ? packet.getPacketStatus() : "NULL"));
        System.out.println("[RELAY-1] Hop Count: " + (packet != null ? packet.getHopCount() : "NULL"));
        System.out.println("[RELAY-1] Max Hop Count: " + (packet != null ? packet.getMaxHopCount() : "NULL"));
        System.out.println("[RELAY-1] TTL: " + (packet != null ? packet.getTtl() : "NULL"));
        System.out.println("[RELAY-1] Current Node: " + (packet != null ? packet.getCurrentNode() : "NULL"));
        System.out.println("[RELAY-1] Next Node: " + (packet != null ? packet.getNextNode() : "NULL"));
        System.out.println("[RELAY-1] Route History: " + (packet != null ? packet.getRouteHistory() : "NULL"));
        System.out.println("[RELAY-1] Has Encrypted Data: " + (packet != null && packet.getEncryptedData() != null));
        System.out.println("[RELAY-1] Has Encrypted Key: " + (packet != null && packet.getEncryptedKey() != null));
        System.out.println("[RELAY-1] Has IV: " + (packet != null && packet.getIv() != null));
        System.out.println("[RELAY-1] Has Checksum: " + (packet != null && packet.getChecksum() != null));
        System.out.println("========================================");

        log.info("Packet Received: Ingested packet ID {} at Relay-1", packet.getPacketId());

        // 1. Validate TTL has not expired
        System.out.println("[RELAY-1] STEP 1: Validating TTL...");
        if (packet.getTtl() != null && LocalDateTime.now().isAfter(packet.getTtl())) {
            System.out.println("[RELAY-1] STEP 1 FAILED: TTL EXPIRED! TTL=" + packet.getTtl() + " NOW=" + LocalDateTime.now());
            log.warn("Packet DROPPED: TTL expired for packet ID {}", packet.getPacketId());
            packet.setPacketStatus(PacketStatus.DROPPED);
            publishEvent(packet, "RELAY_1", "FAILED", "Relay 1 Drop: TTL expired");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(packet);
        }
        System.out.println("[RELAY-1] STEP 1 PASSED: TTL is valid");

        // 2. Validate Hop Limit
        System.out.println("[RELAY-1] STEP 2: Validating Hop Count... hopCount=" + packet.getHopCount() + " maxHopCount=" + packet.getMaxHopCount());
        if (packet.getHopCount() >= packet.getMaxHopCount()) {
            System.out.println("[RELAY-1] STEP 2 FAILED: HOP COUNT EXCEEDED!");
            log.warn("Packet DROPPED: Max hop count exceeded for packet ID {}", packet.getPacketId());
            packet.setPacketStatus(PacketStatus.DROPPED);
            publishEvent(packet, "RELAY_1", "FAILED", "Relay 1 Drop: Max hops exceeded");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(packet);
        }
        System.out.println("[RELAY-1] STEP 2 PASSED: Hop count is within limits");

        // 3. Update packet metadata
        System.out.println("[RELAY-1] STEP 3: Updating packet metadata...");
        packet.setHopCount(packet.getHopCount() + 1);
        if (packet.getRouteHistory() == null) {
            packet.setRouteHistory(new ArrayList<>());
        }
        packet.getRouteHistory().add("Relay-1");
        packet.setCurrentNode("Relay-1");
        packet.setNextNode("Relay-2");
        packet.setPacketStatus(PacketStatus.RECEIVED);
        System.out.println("[RELAY-1] STEP 3 DONE: hopCount=" + packet.getHopCount() + " currentNode=Relay-1 nextNode=Relay-2 routeHistory=" + packet.getRouteHistory());

        System.out.println("[RELAY-1] STEP 3.5: Publishing ROUTING event to transaction-service...");
        publishEvent(packet, "RELAY_1", "ROUTING", "Relay 1 Received: Packet received from upstream");
        System.out.println("[RELAY-1] STEP 3.5 DONE: Event published");

        // 4. Forward packet using REST with 3 retries (2s delay)
        int attempt = 0;
        String targetUrl = nextNodeUrl + "/api/relay/receive";
        System.out.println("[RELAY-1] STEP 4: Forwarding packet to Relay-2 at URL: " + targetUrl);

        while (attempt < 3) {
            try {
                if (attempt > 0) {
                    System.out.println("[RELAY-1] STEP 4 RETRY: Attempt " + (attempt + 1) + " to forward to Relay-2");
                    publishEvent(packet, "RELAY_1", "ROUTING", "Retry Started: Attempt " + (attempt + 1) + " to forward packet to Relay-2");
                }
                System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": Sending POST to " + targetUrl);
                log.info("Packet Forwarded: Forwarding packet ID {} to Relay-2 (Attempt {})", packet.getPacketId(), attempt + 1);
                packet.setPacketStatus(PacketStatus.FORWARDED);
                
                System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": About to call restTemplate.postForEntity...");
                long startTime = System.currentTimeMillis();
                ResponseEntity<Packet> response = restTemplate.postForEntity(targetUrl, packet, Packet.class);
                long elapsed = System.currentTimeMillis() - startTime;
                
                System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": Response received in " + elapsed + "ms");
                System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": Response Status Code: " + response.getStatusCode());
                System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": Response Has Body: " + (response.getBody() != null));
                
                if (response.getBody() != null) {
                    Packet respBody = response.getBody();
                    System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": Response Packet Status: " + respBody.getPacketStatus());
                    System.out.println("[RELAY-1] STEP 4." + (attempt + 1) + ": Response Current Node: " + respBody.getCurrentNode());
                }

                if (response.getStatusCode().is2xxSuccessful()) {
                    Packet responseBody = response.getBody();
                    System.out.println("[RELAY-1] STEP 4 SUCCESS: Packet forwarded to Relay-2 successfully!");
                    publishEvent(packet, "RELAY_1", "ROUTING", "Relay 1 Forwarded: Packet forwarded to Relay-2 successfully");
                    return ResponseEntity.ok(responseBody);
                }
                System.out.println("[RELAY-1] STEP 4 ERROR: Non-2xx response: " + response.getStatusCode());
                throw new RuntimeException("Downstream response failure status: " + response.getStatusCode());
            } catch (Exception e) {
                attempt++;
                System.out.println("[RELAY-1] STEP 4 EXCEPTION on attempt " + attempt + ": " + e.getClass().getName() + " - " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("[RELAY-1] STEP 4 ROOT CAUSE: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
                }
                log.warn("Retry Attempt {}: Failed to forward packet ID {}. Error: {}", attempt, packet.getPacketId(), e.getMessage());
                if (attempt >= 3) {
                    System.out.println("[RELAY-1] STEP 4 FINAL FAILURE: All 3 retries exhausted!");
                    log.error("Packet Failure: Exhausted all retries. Packet ID {} routing failed.", packet.getPacketId());
                    packet.setPacketStatus(PacketStatus.FAILED);
                    publishEvent(packet, "RELAY_1", "FAILED", "Packet Failure: Failed to forward packet to Relay-2 after all retries");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(packet);
                }
                System.out.println("[RELAY-1] STEP 4 WAITING: Sleeping 2s before retry...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }

        System.out.println("[RELAY-1] STEP 5 FAILURE: Fell through retry loop - marking FAILED");
        packet.setPacketStatus(PacketStatus.FAILED);
        publishEvent(packet, "RELAY_1", "FAILED", "Packet Failure: Relay-1 failed downstream forwarding");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(packet);
    }

    private void publishEvent(Packet packet, String stage, String txnStatus, String message) {
        String eventUrl = transactionServiceUrl + "/api/transactions/events";
        System.out.println("[RELAY-1] EVENT: Publishing event to " + eventUrl);
        System.out.println("[RELAY-1] EVENT: Stage=" + stage + " TxnStatus=" + txnStatus + " Message=" + message);
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(packet.getTransactionId())
                    .timestamp(LocalDateTime.now())
                    .serviceName("Relay-1")
                    .currentStage(stage)
                    .transactionStatus(txnStatus)
                    .packetStatus(packet.getPacketStatus() != null ? packet.getPacketStatus().name() : null)
                    .hopCount(packet.getHopCount())
                    .routeHistory(packet.getRouteHistory())
                    .message(message)
                    .build();
            restTemplate.postForEntity(eventUrl, event, Void.class);
            System.out.println("[RELAY-1] EVENT: Successfully published event");
        } catch (Exception e) {
            System.out.println("[RELAY-1] EVENT FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            log.warn("Failed to publish event to Transaction Service: {}", e.getMessage());
        }
    }
}
