package com.meshpay.transactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshpay.common.crypto.HybridEncryptionService;
import com.meshpay.common.dto.EncryptedPayload;
import com.meshpay.common.dto.UserDto;
import com.meshpay.common.dto.Packet;
import com.meshpay.common.dto.PacketStatus;
import com.meshpay.common.dto.TransactionEvent;
import com.meshpay.common.hash.HashService;
import com.meshpay.transactionservice.client.BankClient;
import com.meshpay.transactionservice.dto.CreateTransactionRequest;
import com.meshpay.transactionservice.dto.TransactionResponse;
import com.meshpay.transactionservice.entity.Transaction;
import com.meshpay.transactionservice.entity.TransactionStatus;
import com.meshpay.transactionservice.exception.DuplicateResourceException;
import com.meshpay.transactionservice.exception.ResourceNotFoundException;
import com.meshpay.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankClient bankClient;
    private final SimpMessagingTemplate messagingTemplate;
    private RestTemplate restTemplate;

    {
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
        System.out.println("[TXN-SERVICE] RestTemplate initialized with JavaTimeModule");
        System.out.println("========================================");
    }

    private final java.util.Map<UUID, List<TransactionEvent>> eventCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Value("${services.next-node.url}")
    private String nextNodeUrl;

    public TransactionResponse createTransaction(CreateTransactionRequest request, UserDto sender) {
        System.out.println("========================================");
        System.out.println("[TXN-SERVICE] CREATE TRANSACTION START");
        System.out.println("[TXN-SERVICE] Sender: " + sender.getEmail());
        System.out.println("[TXN-SERVICE] Receiver UPI: " + request.getReceiverUpiId());
        System.out.println("[TXN-SERVICE] Amount: " + request.getAmount());
        System.out.println("========================================");
        log.info("Transaction Created: Starting creation process for user {}", sender.getEmail());

        // 1. Get Sender UPI ID from authenticated user
        String senderUpiId = sender.getUpiId();
        if (senderUpiId == null || senderUpiId.isBlank()) {
            throw new IllegalArgumentException("Sender does not have a UPI ID. Please complete registration.");
        }

        // 2. Validate self-transfer
        if (senderUpiId.equalsIgnoreCase(request.getReceiverUpiId().trim())) {
            throw new IllegalArgumentException("Self-transfer is not allowed");
        }

        // 3. Generate Idempotency Key
        long timestamp = System.currentTimeMillis();
        String idempotencyRaw = sender.getId() + "*" + request.getReceiverUpiId().trim() + "*" + request.getAmount().toString() + "*" + timestamp;
        String idempotencyKey = HashService.generateHash(idempotencyRaw);

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateResourceException("Duplicate transaction request detected (Idempotency conflict)");
        }

        // 4. Generate transaction identifiers & TTL
        UUID transactionId = UUID.randomUUID();
        LocalDateTime ttl = LocalDateTime.now().plusMinutes(60);

        // Publish Created Event
        broadcastEvent(transactionId, "Transaction-Service", "CREATED", "CREATED", "RECEIVED", 0, new ArrayList<>(List.of("Transaction-Service")), "Transaction Created: Starting creation process for user " + sender.getEmail());

        // 5. Construct transaction payload JSON representation
        String payloadJson = String.format(
                "{\"transactionId\":\"%s\",\"senderUpiId\":\"%s\",\"receiverUpiId\":\"%s\",\"amount\":%s}",
                transactionId,
                senderUpiId,
                request.getReceiverUpiId().trim(),
                request.getAmount()
        );

        // 6. Create Checksum (SHA-256 hash of original plain text payload)
        String checksum = HashService.generateHash(payloadJson);

        System.out.println("[TXN-SERVICE] STEP: Fetching Bank Public Key...");
        log.info("Transaction Encrypted: Fetching Bank Public Key to encrypt transaction payload...");
        
        // 7. Retrieve Bank RSA public key
        PublicKey bankPublicKey = bankClient.getBankPublicKey();

        // 8. Execute hybrid encryption
        EncryptedPayload encryptedPayload = HybridEncryptionService.encrypt(payloadJson, bankPublicKey);

        // Publish Encrypted Event
        broadcastEvent(transactionId, "Transaction-Service", "ENCRYPTED", "ENCRYPTED", "RECEIVED", 0, new ArrayList<>(List.of("Transaction-Service")), "Payload Encrypted: Secured transaction using Bank RSA public key");

        // 9. Map and save entity directly in ROUTING status
        Transaction transaction = Transaction.builder()
                .transactionId(transactionId)
                .senderUserId(sender.getId())
                .senderEmail(sender.getEmail())
                .senderUpiId(senderUpiId)
                .receiverUpiId(request.getReceiverUpiId().trim())
                .amount(request.getAmount())
                .status(TransactionStatus.ROUTING)
                .ttl(ttl)
                .encrypted(true)
                .retryCount(0)
                .idempotencyKey(idempotencyKey)
                .checksum(checksum)
                .packetVersion("1.0")
                .encryptedData(encryptedPayload.getEncryptedData())
                .encryptedKey(encryptedPayload.getEncryptedKey())
                .iv(encryptedPayload.getIv())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        System.out.println("[TXN-SERVICE] STEP: Transaction " + transactionId + " saved to DB with status ROUTING");
        log.info("Transaction Stored: Transaction {} stored with status ROUTING", transactionId);

        // Build the Packet DTO
        List<String> routeHistory = new ArrayList<>();
        routeHistory.add("Transaction-Service");

        Packet packet = Packet.builder()
                .packetId(UUID.randomUUID().toString())
                .transactionId(transactionId)
                .encryptedData(encryptedPayload.getEncryptedData())
                .encryptedKey(encryptedPayload.getEncryptedKey())
                .iv(encryptedPayload.getIv())
                .checksum(checksum)
                .ttl(ttl)
                .hopCount(0)
                .maxHopCount(5)
                .currentNode("Transaction-Service")
                .nextNode("Relay-1")
                .createdAt(LocalDateTime.now())
                .routeHistory(routeHistory)
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        // Publish Routing Initiated Event
        System.out.println("[TXN-SERVICE] STEP: Broadcasting RELAY_1 routing event via WebSocket");
        broadcastEvent(transactionId, "Transaction-Service", "RELAY_1", "ROUTING", "FORWARDED", 0, new ArrayList<>(List.of("Transaction-Service")), "Packet Sent to Relay 1");

        // Forward packet with retries asynchronously to give WebSocket time to connect
        System.out.println("[TXN-SERVICE] STEP: Launching async CompletableFuture to send packet to Relay-1");
        System.out.println("[TXN-SERVICE] STEP: Target URL will be: " + nextNodeUrl + "/api/relay/receive");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                System.out.println("[TXN-SERVICE] ASYNC: Sleeping 500ms to allow WebSocket connection...");
                Thread.sleep(500); // 500ms delay to allow client websocket connection
                System.out.println("[TXN-SERVICE] ASYNC: Sleep complete, calling sendPacketWithRetry");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendPacketWithRetry(packet, saved.getTransactionId());
        });

        System.out.println("[TXN-SERVICE] STEP: Returning response to frontend (async forwarding in background)");
        return mapToResponse(saved);
    }

    private void sendPacketWithRetry(Packet packet, UUID transactionId) {
        int attempt = 0;
        String targetUrl = nextNodeUrl + "/api/relay/receive";
        System.out.println("[TXN-SERVICE] SEND_PACKET: Starting sendPacketWithRetry");
        System.out.println("[TXN-SERVICE] SEND_PACKET: Target URL: " + targetUrl);
        System.out.println("[TXN-SERVICE] SEND_PACKET: Packet ID: " + packet.getPacketId());
        System.out.println("[TXN-SERVICE] SEND_PACKET: Transaction ID: " + transactionId);

        while (attempt < 3) {
            try {
                if (attempt > 0) {
                    System.out.println("[TXN-SERVICE] SEND_PACKET: Retry attempt " + (attempt + 1));
                    broadcastEvent(packet.getTransactionId(), "Transaction-Service", "RELAY_1", "ROUTING", "RECEIVED", 0, new ArrayList<>(List.of("Transaction-Service")), "Retry Started: Attempt " + (attempt + 1) + " to forward packet to Relay-1");
                }
                System.out.println("[TXN-SERVICE] SEND_PACKET: Calling POST to " + targetUrl + " (attempt " + (attempt + 1) + ")");
                log.info("Retry Attempt {}: Forwarding packet ID {} to Relay-1 (Attempt {})", attempt + 1, packet.getPacketId(), attempt + 1);
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<Packet> response = restTemplate.postForEntity(targetUrl, packet, Packet.class);
                long elapsed = System.currentTimeMillis() - startTime;
                
                System.out.println("[TXN-SERVICE] SEND_PACKET: Response received in " + elapsed + "ms");
                System.out.println("[TXN-SERVICE] SEND_PACKET: Status Code: " + response.getStatusCode());
                System.out.println("[TXN-SERVICE] SEND_PACKET: Has Body: " + (response.getBody() != null));
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Packet ackPacket = response.getBody();
                    System.out.println("[TXN-SERVICE] SEND_PACKET: Ack Packet Status: " + ackPacket.getPacketStatus());
                    if (ackPacket.getPacketStatus() == PacketStatus.FAILED) {
                        System.out.println("[TXN-SERVICE] SEND_PACKET: Downstream returned FAILED status!");
                        throw new RuntimeException("Downstream packet status returned FAILED");
                    }
                    if (attempt > 0) {
                        broadcastEvent(packet.getTransactionId(), "Transaction-Service", "RELAY_1", "ROUTING", "FORWARDED", 0, new ArrayList<>(List.of("Transaction-Service")), "Retry Completed: Packet forwarded to Relay-1 successfully");
                    }
                    System.out.println("[TXN-SERVICE] SEND_PACKET: SUCCESS - Packet forwarded to Relay-1!");
                    log.info("Packet Forwarded: Packet ID {} successfully routed downstream. Next node ack status: {}", 
                            packet.getPacketId(), ackPacket.getPacketStatus());
                    return;
                }
                System.out.println("[TXN-SERVICE] SEND_PACKET: Non-2xx or null body!");
                throw new RuntimeException("REST call returned code: " + response.getStatusCode());
            } catch (Exception e) {
                attempt++;
                System.out.println("[TXN-SERVICE] SEND_PACKET EXCEPTION on attempt " + attempt + ": " + e.getClass().getName() + " - " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("[TXN-SERVICE] SEND_PACKET ROOT CAUSE: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
                }
                log.warn("Retry Attempt {}: Failed to send packet ID {} to Relay-1. Error: {}", attempt, packet.getPacketId(), e.getMessage());
                if (attempt >= 3) {
                    System.out.println("[TXN-SERVICE] SEND_PACKET FINAL FAILURE: All 3 retries exhausted!");
                    log.error("Packet Failure: Exhausted all retries. Packet ID {} failed to route.", packet.getPacketId());
                    // Update database transaction status to FAILED
                    updateTransactionStatusToFailed(transactionId);
                    broadcastEvent(packet.getTransactionId(), "Transaction-Service", "RELAY_1", "FAILED", "FAILED", 0, new ArrayList<>(List.of("Transaction-Service")), "Packet Failure: Failed to route packet to Relay-1 after all retries");
                    return;
                }
                System.out.println("[TXN-SERVICE] SEND_PACKET: Sleeping 2s before retry...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    private void updateTransactionStatusToFailed(UUID transactionId) {
        try {
            Transaction transaction = transactionRepository.findByTransactionId(transactionId).orElse(null);
            if (transaction != null) {
                if (transaction.getStatus() != TransactionStatus.SUCCESS) {
                    transaction.setStatus(TransactionStatus.FAILED);
                    transactionRepository.save(transaction);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update transaction status to FAILED in database: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getSenderTransactions(String email) {
        return transactionRepository.findAllBySenderEmail(email).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getUserTransactions(String upiId) {
        List<Transaction> sent = transactionRepository.findAllBySenderUpiId(upiId);
        List<Transaction> received = transactionRepository.findAllByReceiverUpiId(upiId);

        // Merge and deduplicate by transactionId, then sort by creation date descending
        java.util.Map<UUID, Transaction> merged = new java.util.LinkedHashMap<>();
        for (Transaction t : sent) {
            merged.put(t.getTransactionId(), t);
        }
        for (Transaction t : received) {
            merged.putIfAbsent(t.getTransactionId(), t);
        }

        return merged.values().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByUuid(UUID transactionId) {
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        return mapToResponse(transaction);
    }

    @Transactional
    public void updatePacketStatus(Packet packet) {
        Transaction transaction = transactionRepository.findByTransactionIdForUpdate(packet.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found for ID: " + packet.getTransactionId()));

        log.info("Transaction status notification received: Transaction ID {} Node: {} Status: {}", 
                packet.getTransactionId(), packet.getCurrentNode(), packet.getPacketStatus());

        String stage = "ROUTING";
        String txnStatus = "ROUTING";

        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            stage = "COMPLETED";
            txnStatus = "SUCCESS";
        } else {
            if (packet.getPacketStatus() == PacketStatus.FAILED) {
                transaction.setStatus(TransactionStatus.FAILED);
                txnStatus = "FAILED";
                stage = "COMPLETED";
            } else if (packet.getPacketStatus() == PacketStatus.RECEIVED && "Gateway".equals(packet.getCurrentNode())) {
                transaction.setGatewayNode("Gateway");
                transaction.setStatus(TransactionStatus.ROUTING);
                stage = "GATEWAY";
            } else if (packet.getPacketStatus() == PacketStatus.DELIVERED_TO_GATEWAY) {
                transaction.setGatewayNode("Gateway");
                transaction.setStatus(TransactionStatus.ROUTING);
                stage = "GATEWAY";
            } else if (packet.getPacketStatus() == PacketStatus.FORWARDED && "Gateway".equals(packet.getCurrentNode())) {
                transaction.setStatus(TransactionStatus.ROUTING);
                stage = "GATEWAY";
            }
        }

        transactionRepository.save(transaction);

        broadcastEvent(packet.getTransactionId(), packet.getCurrentNode(), stage, txnStatus, packet.getPacketStatus().name(), packet.getHopCount(), packet.getRouteHistory(), "Packet updated downstream at " + packet.getCurrentNode() + " to " + packet.getPacketStatus());
    }

    private boolean canTransition(TransactionStatus currentStatus, TransactionStatus newStatus) {
        if (currentStatus == TransactionStatus.SUCCESS) {
            return false; // Once SUCCESS, it is final
        }
        if (currentStatus == TransactionStatus.FAILED || currentStatus == TransactionStatus.EXPIRED) {
            // FAILED or EXPIRED can only transition to SUCCESS
            return newStatus == TransactionStatus.SUCCESS;
        }
        return true;
    }

    @Transactional
    public void processTransactionEvent(TransactionEvent event) {
        log.info("Processing TransactionEvent: ID {} Stage {} Status {}", 
                event.getTransactionId(), event.getCurrentStage(), event.getTransactionStatus());
        
        Transaction transaction = transactionRepository.findByTransactionIdForUpdate(event.getTransactionId()).orElse(null);
        if (transaction != null) {
            if (event.getTransactionStatus() != null) {
                try {
                    TransactionStatus newStatus = TransactionStatus.valueOf(event.getTransactionStatus());
                    TransactionStatus currentStatus = transaction.getStatus();
                    
                    if (canTransition(currentStatus, newStatus)) {
                        transaction.setStatus(newStatus);
                    }
                } catch (Exception ignored) {}
            }
            if ("Relay-1".equals(event.getServiceName())) {
                transaction.setRelayNode("Relay-1");
            } else if ("Relay-2".equals(event.getServiceName())) {
                transaction.setRelayNode("Relay-2");
            } else if ("Gateway".equals(event.getServiceName())) {
                transaction.setGatewayNode("Gateway");
            }
            transactionRepository.save(transaction);
        }
        
        // Cache event too!
        eventCache.computeIfAbsent(event.getTransactionId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(event);

        messagingTemplate.convertAndSend("/topic/transactions/" + event.getTransactionId(), event);
    }

    private void broadcastEvent(UUID transactionId, String serviceName, String stage, String txnStatus, String pktStatus, int hops, List<String> history, String message) {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(transactionId)
                .timestamp(LocalDateTime.now())
                .serviceName(serviceName)
                .currentStage(stage)
                .transactionStatus(txnStatus)
                .packetStatus(pktStatus)
                .hopCount(hops)
                .routeHistory(history)
                .message(message)
                .build();
        
        // Cache event!
        eventCache.computeIfAbsent(transactionId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(event);

        log.info("Broadcasting WebSocket event: [Stage: {}, Message: {}]", stage, message);
        messagingTemplate.convertAndSend("/topic/transactions/" + transactionId, event);
    }

    public List<TransactionEvent> getTransactionEvents(UUID transactionId) {
        List<TransactionEvent> cached = eventCache.get(transactionId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // Fallback: Reconstruct from DB
        Transaction txn = transactionRepository.findByTransactionId(transactionId).orElse(null);
        if (txn == null) {
            return java.util.Collections.emptyList();
        }

        List<TransactionEvent> events = new ArrayList<>();
        LocalDateTime baseTime = txn.getCreatedAt() != null ? txn.getCreatedAt() : LocalDateTime.now();

        // 1. Created Event
        events.add(TransactionEvent.builder()
                .transactionId(transactionId)
                .timestamp(baseTime)
                .serviceName("Transaction-Service")
                .currentStage("CREATED")
                .transactionStatus("CREATED")
                .packetStatus("RECEIVED")
                .hopCount(0)
                .routeHistory(new ArrayList<>(List.of("Transaction-Service")))
                .message("Transaction Created: Reconstructed from Database")
                .build());

        // 2. Encrypted Event
        events.add(TransactionEvent.builder()
                .transactionId(transactionId)
                .timestamp(baseTime.plusSeconds(1))
                .serviceName("Transaction-Service")
                .currentStage("ENCRYPTED")
                .transactionStatus("ENCRYPTED")
                .packetStatus("RECEIVED")
                .hopCount(0)
                .routeHistory(new ArrayList<>(List.of("Transaction-Service")))
                .message("Payload Encrypted: Secured transaction using Bank RSA public key")
                .build());

        // 3. Relay-1/Relay-2 if they processed it
        List<String> routeHistory = new ArrayList<>(List.of("Transaction-Service"));
        if (txn.getRelayNode() != null) {
            routeHistory.add("Relay-1");
            events.add(TransactionEvent.builder()
                    .transactionId(transactionId)
                    .timestamp(baseTime.plusSeconds(2))
                    .serviceName("Relay-1")
                    .currentStage("RELAY_1")
                    .transactionStatus("ROUTING")
                    .packetStatus("FORWARDED")
                    .hopCount(1)
                    .routeHistory(new ArrayList<>(routeHistory))
                    .message("Relay 1 Forwarded: Packet forwarded successfully")
                    .build());

            if (txn.getRelayNode().contains("Relay-2") || txn.getGatewayNode() != null || txn.getStatus() == TransactionStatus.SUCCESS) {
                routeHistory.add("Relay-2");
                events.add(TransactionEvent.builder()
                        .transactionId(transactionId)
                        .timestamp(baseTime.plusSeconds(3))
                        .serviceName("Relay-2")
                        .currentStage("RELAY_2")
                        .transactionStatus("ROUTING")
                        .packetStatus("FORWARDED")
                        .hopCount(2)
                        .routeHistory(new ArrayList<>(routeHistory))
                        .message("Relay 2 Forwarded: Packet forwarded successfully")
                        .build());
            }
        }

        // 4. Gateway if processed
        if (txn.getGatewayNode() != null) {
            routeHistory.add("Gateway");
            events.add(TransactionEvent.builder()
                    .transactionId(transactionId)
                    .timestamp(baseTime.plusSeconds(4))
                    .serviceName("Gateway")
                    .currentStage("GATEWAY")
                    .transactionStatus("ROUTING")
                    .packetStatus("FORWARDED")
                    .hopCount(3)
                    .routeHistory(new ArrayList<>(routeHistory))
                    .message("Gateway Forwarded: Packet delivered to Bank")
                    .build());
        }

        // 5. Final Status
        if (txn.getStatus() == TransactionStatus.SUCCESS) {
            routeHistory.add("Bank");
            events.add(TransactionEvent.builder()
                    .transactionId(transactionId)
                    .timestamp(baseTime.plusSeconds(5))
                    .serviceName("Bank-Service")
                    .currentStage("BANK")
                    .transactionStatus("SUCCESS")
                    .packetStatus("DELIVERED")
                    .hopCount(4)
                    .routeHistory(new ArrayList<>(routeHistory))
                    .message("Bank Settled: Payment successfully completed")
                    .build());

            events.add(TransactionEvent.builder()
                    .transactionId(transactionId)
                    .timestamp(baseTime.plusSeconds(6))
                    .serviceName("Bank-Service")
                    .currentStage("COMPLETED")
                    .transactionStatus("SUCCESS")
                    .packetStatus("DELIVERED")
                    .hopCount(4)
                    .routeHistory(new ArrayList<>(routeHistory))
                    .message("Transaction Completed Successfully")
                    .build());
        } else if (txn.getStatus() == TransactionStatus.FAILED) {
            events.add(TransactionEvent.builder()
                    .transactionId(transactionId)
                    .timestamp(baseTime.plusSeconds(5))
                    .serviceName("Transaction-Service")
                    .currentStage("COMPLETED")
                    .transactionStatus("FAILED")
                    .packetStatus("FAILED")
                    .hopCount(routeHistory.size() - 1)
                    .routeHistory(new ArrayList<>(routeHistory))
                    .message("Transaction Failed")
                    .build());
        }

        return events;
    }

    private TransactionResponse mapToResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .transactionId(txn.getTransactionId())
                .senderUserId(txn.getSenderUserId())
                .senderEmail(txn.getSenderEmail())
                .senderUpiId(txn.getSenderUpiId())
                .receiverUpiId(txn.getReceiverUpiId())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .ttl(txn.getTtl())
                .createdAt(txn.getCreatedAt())
                .updatedAt(txn.getUpdatedAt())
                .encrypted(txn.isEncrypted())
                .relayNode(txn.getRelayNode())
                .gatewayNode(txn.getGatewayNode())
                .retryCount(txn.getRetryCount())
                .idempotencyKey(txn.getIdempotencyKey())
                .checksum(txn.getChecksum())
                .packetVersion(txn.getPacketVersion())
                .encryptedData(txn.getEncryptedData())
                .encryptedKey(txn.getEncryptedKey())
                .iv(txn.getIv())
                .build();
    }
}
