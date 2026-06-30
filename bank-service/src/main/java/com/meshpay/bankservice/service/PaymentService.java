package com.meshpay.bankservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshpay.bankservice.dto.DecryptedTransactionPayload;
import com.meshpay.bankservice.dto.PaymentResponse;
import com.meshpay.bankservice.entity.*;
import com.meshpay.bankservice.repository.AccountRepository;
import com.meshpay.bankservice.repository.LedgerEntryRepository;
import com.meshpay.bankservice.repository.ProcessedPaymentRepository;
import com.meshpay.common.crypto.HybridEncryptionService;
import com.meshpay.common.dto.EncryptedPayload;
import com.meshpay.common.dto.Packet;
import com.meshpay.common.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final ProcessedPaymentRepository processedPaymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final KeyPair bankKeyPair;
    private org.springframework.web.client.RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    {
        // Configure RestTemplate with JavaTimeModule for proper LocalDateTime serialization
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(om);

        this.restTemplate = new org.springframework.web.client.RestTemplate();
        this.restTemplate.getMessageConverters().removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        this.restTemplate.getMessageConverters().add(0, converter);
        
        this.objectMapper = om;
        
        System.out.println("========================================");
        System.out.println("[BANK-SERVICE] RestTemplate initialized with JavaTimeModule");
        System.out.println("========================================");
    }

    @org.springframework.beans.factory.annotation.Value("${services.transaction-service.url:http://localhost:8086}")
    private String transactionServiceUrl;

    @Transactional
    public PaymentResponse processPayment(Packet packet) {
        System.out.println("========================================");
        System.out.println("[BANK-SERVICE] STEP 0: /api/payments/process ENDPOINT HIT");
        System.out.println("[BANK-SERVICE] Packet ID: " + (packet != null ? packet.getPacketId() : "NULL"));
        System.out.println("[BANK-SERVICE] Transaction ID: " + (packet != null ? packet.getTransactionId() : "NULL"));
        System.out.println("[BANK-SERVICE] Has Encrypted Data: " + (packet != null && packet.getEncryptedData() != null));
        System.out.println("[BANK-SERVICE] Has Encrypted Key: " + (packet != null && packet.getEncryptedKey() != null));
        System.out.println("[BANK-SERVICE] Has IV: " + (packet != null && packet.getIv() != null));
        System.out.println("[BANK-SERVICE] Has Checksum: " + (packet != null && packet.getChecksum() != null));
        System.out.println("========================================");
        log.info("Packet received: Processing secure payment for packet ID {}", packet != null ? packet.getPacketId() : "null");

        // Step 1: Validate packet properties
        System.out.println("[BANK-SERVICE] STEP 1: Validating packet properties...");
        if (packet == null || packet.getChecksum() == null || packet.getEncryptedData() == null
                || packet.getEncryptedKey() == null || packet.getIv() == null) {
            System.out.println("[BANK-SERVICE] STEP 1 FAILED: Malformed packet!");
            log.error("Payment processing failed: Malformed packet.");
            throw new IllegalArgumentException("Malformed packet details");
        }
        System.out.println("[BANK-SERVICE] STEP 1 PASSED: Packet properties valid");

        // Step 2: Validate TTL
        System.out.println("[BANK-SERVICE] STEP 2: Validating TTL...");
        if (packet.getTtl() != null && LocalDateTime.now().isAfter(packet.getTtl())) {
            System.out.println("[BANK-SERVICE] STEP 2 FAILED: TTL expired!");
            log.warn("Packet DROPPED: TTL expired for transaction ID {}", packet.getTransactionId());
            publishEvent(packet, "BANK", "EXPIRED", "Payment Failed: Packet TTL expired");
            return PaymentResponse.builder()
                    .transactionId(packet.getTransactionId())
                    .status("EXPIRED")
                    .message("Packet TTL expired")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        System.out.println("[BANK-SERVICE] STEP 2 PASSED: TTL valid");

        // Step 3: Validate idempotency
        System.out.println("[BANK-SERVICE] STEP 3: Checking idempotency...");
        Optional<ProcessedPayment> existingPayment = processedPaymentRepository.findByTransactionId(packet.getTransactionId());
        if (existingPayment.isPresent()) {
            System.out.println("[BANK-SERVICE] STEP 3: Idempotency match - already processed!");
            ProcessedPayment payment = existingPayment.get();
            log.info("Idempotency match: Transaction {} already processed. Returning previous response.", packet.getTransactionId());
            publishEvent(packet, "BANK", payment.getStatus(), "Payment already processed (Idempotent replay)");
            return PaymentResponse.builder()
                    .transactionId(payment.getTransactionId())
                    .status(payment.getStatus())
                    .message("Payment already processed (Idempotent replay)")
                    .processedAt(payment.getProcessedAt())
                    .senderBalance(payment.getSenderBalance())
                    .receiverBalance(payment.getReceiverBalance())
                    .build();
        }
        System.out.println("[BANK-SERVICE] STEP 3 PASSED: Not a duplicate");
        
        // Bank stage is Hop 4
        packet.setHopCount(packet.getHopCount() + 1);

        publishEvent(packet, "BANK", "ROUTING", "Bank Received: Encrypted packet received from Gateway");

        try {
            // Step 4 & 5: Decrypt AES key with RSA private key and decrypt GCM payload
            String decryptedJson;
            try {
                System.out.println("[BANK-SERVICE] STEP 4: Building EncryptedPayload for decryption...");
                EncryptedPayload encryptedPayload = EncryptedPayload.builder()
                        .encryptedData(packet.getEncryptedData())
                        .encryptedKey(packet.getEncryptedKey())
                        .iv(packet.getIv())
                        .checksum(packet.getChecksum())
                        .build();

                // Decrypt & verify checksum
                System.out.println("[BANK-SERVICE] STEP 5: Decrypting payload...");
                decryptedJson = HybridEncryptionService.decrypt(encryptedPayload, bankKeyPair.getPrivate());
                System.out.println("[BANK-SERVICE] STEP 5 PASSED: Decryption successful");
                System.out.println("[BANK-SERVICE] STEP 5: Decrypted JSON (first 100 chars): " + (decryptedJson != null ? decryptedJson.substring(0, Math.min(100, decryptedJson.length())) : "NULL"));
                log.info("Decryption successful: Decrypted transaction payload.");
                log.info("Checksum verified: Verified packet payload integrity signature.");
                publishEvent(packet, "BANK", "ROUTING", "Checksum Verified: Signature matches decrypted payload integrity");
                publishEvent(packet, "BANK", "ROUTING", "Payload Decrypted: Recovered original payment parameters");
            } catch (Exception e) {
                System.out.println("[BANK-SERVICE] STEP 5 FAILED: Decryption error: " + e.getClass().getName() + " - " + e.getMessage());
                log.error("Payment processing failed: Decryption/signature validation failed. {}", e.getMessage());
                throw new SecurityException("Secure validation failure: Decryption/signature verification failed.", e);
            }

            // Step 7: Deserialize decrypted JSON payload
            System.out.println("[BANK-SERVICE] STEP 7: Deserializing decrypted payload...");
            DecryptedTransactionPayload payload;
            try {
                payload = objectMapper.readValue(decryptedJson, DecryptedTransactionPayload.class);
                System.out.println("[BANK-SERVICE] STEP 7 PASSED: Payload deserialized. Sender=" + payload.getSenderUpiId() + " Receiver=" + payload.getReceiverUpiId() + " Amount=" + payload.getAmount());
            } catch (Exception e) {
                System.out.println("[BANK-SERVICE] STEP 7 FAILED: " + e.getClass().getName() + " - " + e.getMessage());
                log.error("Deserialization failed for decrypted JSON: {}", e.getMessage());
                throw new IllegalArgumentException("Failed to parse decrypted transaction JSON", e);
            }

            publishEvent(packet, "BANK", "ROUTING", "Payment Processing Started: Performing account checks and balance validations");

            // Step 8: Validate accounts and balances
            System.out.println("[BANK-SERVICE] STEP 8: Looking up sender account: " + payload.getSenderUpiId());
            Account senderAccount = accountRepository.findByUpiId(payload.getSenderUpiId())
                    .orElseThrow(() -> new IllegalArgumentException("Sender account not found with UPI ID: " + payload.getSenderUpiId()));
            System.out.println("[BANK-SERVICE] STEP 8: Looking up receiver account: " + payload.getReceiverUpiId());
            Account receiverAccount = accountRepository.findByUpiId(payload.getReceiverUpiId())
                    .orElseGet(() -> {
                        System.out.println("[BANK-SERVICE] STEP 8: Auto-creating receiver account for UPI ID: " + payload.getReceiverUpiId());
                        Account newAcc = Account.builder()
                                .accountNumber("ACC-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                                .accountHolderName(payload.getReceiverUpiId().split("@")[0].toUpperCase())
                                .upiId(payload.getReceiverUpiId())
                                .currentBalance(java.math.BigDecimal.ZERO)
                                .accountStatus(AccountStatus.ACTIVE)
                                .build();
                        return accountRepository.save(newAcc);
                    });

            System.out.println("[BANK-SERVICE] STEP 8: Sender balance=" + senderAccount.getCurrentBalance() + " status=" + senderAccount.getAccountStatus());
            System.out.println("[BANK-SERVICE] STEP 8: Receiver balance=" + receiverAccount.getCurrentBalance() + " status=" + receiverAccount.getAccountStatus());

            if (senderAccount.getAccountStatus() != AccountStatus.ACTIVE) {
                System.out.println("[BANK-SERVICE] STEP 8 FAILED: Sender account BLOCKED");
                throw new IllegalStateException("Sender account status is BLOCKED");
            }
            if (receiverAccount.getAccountStatus() != AccountStatus.ACTIVE) {
                System.out.println("[BANK-SERVICE] STEP 8 FAILED: Receiver account BLOCKED");
                throw new IllegalStateException("Receiver account status is BLOCKED");
            }

            BigDecimal amount = payload.getAmount();
            if (senderAccount.getCurrentBalance().compareTo(amount) < 0) {
                System.out.println("[BANK-SERVICE] STEP 8 FAILED: Insufficient balance! balance=" + senderAccount.getCurrentBalance() + " amount=" + amount);
                throw new IllegalStateException("Insufficient balance");
            }
            System.out.println("[BANK-SERVICE] STEP 8 PASSED: All validations OK");

            // Step 9: Execute payment atomically
            System.out.println("[BANK-SERVICE] STEP 9: Executing payment...");
            senderAccount.setCurrentBalance(senderAccount.getCurrentBalance().subtract(amount));
            receiverAccount.setCurrentBalance(receiverAccount.getCurrentBalance().add(amount));

            accountRepository.save(senderAccount);
            accountRepository.save(receiverAccount);
            System.out.println("[BANK-SERVICE] STEP 9: Account balances updated. Sender new balance=" + senderAccount.getCurrentBalance() + " Receiver new balance=" + receiverAccount.getCurrentBalance());

            // Save immutable ledger entries
            LedgerEntry debitEntry = LedgerEntry.builder()
                    .transactionId(payload.getTransactionId())
                    .accountNumber(senderAccount.getAccountNumber())
                    .entryType(LedgerEntryType.DEBIT)
                    .amount(amount)
                    .balanceAfterTransaction(senderAccount.getCurrentBalance())
                    .build();

            LedgerEntry creditEntry = LedgerEntry.builder()
                    .transactionId(payload.getTransactionId())
                    .accountNumber(receiverAccount.getAccountNumber())
                    .entryType(LedgerEntryType.CREDIT)
                    .amount(amount)
                    .balanceAfterTransaction(receiverAccount.getCurrentBalance())
                    .build();

            ledgerEntryRepository.save(debitEntry);
            ledgerEntryRepository.save(creditEntry);
            System.out.println("[BANK-SERVICE] STEP 9: Ledger entries saved");

            // Step 10: Store processed payment transaction
            System.out.println("[BANK-SERVICE] STEP 10: Saving processed payment record...");
            ProcessedPayment payment = ProcessedPayment.builder()
                    .transactionId(payload.getTransactionId())
                    .status("SUCCESS")
                    .processedAt(LocalDateTime.now())
                    .checksum(packet.getChecksum())
                    .sender(payload.getSenderUpiId())
                    .receiver(payload.getReceiverUpiId())
                    .amount(amount)
                    .senderBalance(senderAccount.getCurrentBalance())
                    .receiverBalance(receiverAccount.getCurrentBalance())
                    .build();

            ProcessedPayment savedPayment = processedPaymentRepository.save(payment);
            System.out.println("[BANK-SERVICE] STEP 10 DONE: Payment saved as SUCCESS");
            log.info("Payment successful: Atomically completed transaction ID {}.", payload.getTransactionId());
            
            // Completed stage is Hop 5
            packet.setHopCount(packet.getHopCount() + 1);
            publishEvent(packet, "COMPLETED", "SUCCESS", "Payment Successful: Atomically completed ledger settlements");

            // Step 11: Return response
            return PaymentResponse.builder()
                    .transactionId(savedPayment.getTransactionId())
                    .status(savedPayment.getStatus())
                    .message("Payment processed successfully")
                    .processedAt(savedPayment.getProcessedAt())
                    .senderBalance(savedPayment.getSenderBalance())
                    .receiverBalance(savedPayment.getReceiverBalance())
                    .build();

        } catch (Exception e) {
            System.out.println("[BANK-SERVICE] PAYMENT FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            packet.setHopCount(packet.getHopCount() + 1);
            publishEvent(packet, "COMPLETED", "FAILED", "Payment Failed: " + e.getMessage());
            throw e;
        }
    }

    private void publishEvent(Packet packet, String stage, String txnStatus, String message) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(packet.getTransactionId())
                    .timestamp(LocalDateTime.now())
                    .serviceName("Bank-Service")
                    .currentStage(stage)
                    .transactionStatus(txnStatus)
                    .packetStatus(packet.getPacketStatus() != null ? packet.getPacketStatus().name() : null)
                    .hopCount(packet.getHopCount())
                    .routeHistory(packet.getRouteHistory())
                    .message(message)
                    .build();
            restTemplate.postForEntity(transactionServiceUrl + "/api/transactions/events", event, Void.class);
        } catch (Exception e) {
            log.warn("Failed to publish event to Transaction Service: {}", e.getMessage());
        }
    }
}
