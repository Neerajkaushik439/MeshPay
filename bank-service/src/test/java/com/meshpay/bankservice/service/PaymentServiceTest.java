package com.meshpay.bankservice.service;

import com.meshpay.bankservice.dto.PaymentResponse;
import com.meshpay.bankservice.entity.*;
import com.meshpay.bankservice.repository.AccountRepository;
import com.meshpay.bankservice.repository.LedgerEntryRepository;
import com.meshpay.bankservice.repository.ProcessedPaymentRepository;
import com.meshpay.common.crypto.HybridEncryptionService;
import com.meshpay.common.crypto.RsaUtility;
import com.meshpay.common.dto.EncryptedPayload;
import com.meshpay.common.dto.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PaymentServiceTest {

    @Mock
    private ProcessedPaymentRepository processedPaymentRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private PaymentService paymentService;

    private KeyPair keyPair;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        keyPair = RsaUtility.generateKeyPair();
        ReflectionTestUtils.setField(paymentService, "bankKeyPair", keyPair);
    }

    private Packet createEncryptedPacket(UUID txnId, String senderUpi, String receiverUpi, BigDecimal amount, LocalDateTime ttl) {
        String json = String.format(
                "{\"transactionId\":\"%s\",\"senderUpiId\":\"%s\",\"receiverUpiId\":\"%s\",\"amount\":%s}",
                txnId, senderUpi, receiverUpi, amount
        );
        EncryptedPayload payload = HybridEncryptionService.encrypt(json, keyPair.getPublic());
        return Packet.builder()
                .packetId("pkt-123")
                .transactionId(txnId)
                .encryptedData(payload.getEncryptedData())
                .encryptedKey(payload.getEncryptedKey())
                .iv(payload.getIv())
                .checksum(payload.getChecksum())
                .ttl(ttl)
                .build();
    }

    @Test
    public void testProcessPaymentSuccess() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.00");
        Packet packet = createEncryptedPacket(txnId, "alice@mesh", "bob@mesh", amount, LocalDateTime.now().plusMinutes(10));

        Account senderAccount = Account.builder()
                .accountNumber("ACC-111")
                .upiId("alice@mesh")
                .currentBalance(new BigDecimal("500.00"))
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        Account receiverAccount = Account.builder()
                .accountNumber("ACC-222")
                .upiId("bob@mesh")
                .currentBalance(new BigDecimal("100.00"))
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        when(processedPaymentRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(accountRepository.findByUpiId("alice@mesh")).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUpiId("bob@mesh")).thenReturn(Optional.of(receiverAccount));
        
        when(processedPaymentRepository.save(any(ProcessedPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentResponse response = paymentService.processPayment(packet);

        // Assert
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(new BigDecimal("350.00"), response.getSenderBalance());
        assertEquals(new BigDecimal("250.00"), response.getReceiverBalance());
        assertEquals(txnId, response.getTransactionId());

        // Verify account updates were saved
        verify(accountRepository, times(1)).save(senderAccount);
        verify(accountRepository, times(1)).save(receiverAccount);
        // Verify ledger entries (DEBIT and CREDIT) were created
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        // Verify processed record saved
        verify(processedPaymentRepository, times(1)).save(any(ProcessedPayment.class));
    }

    @Test
    public void testProcessPaymentExpired() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        Packet packet = createEncryptedPacket(txnId, "alice@mesh", "bob@mesh", new BigDecimal("50.00"), LocalDateTime.now().minusMinutes(1)); // Expired

        // Act
        PaymentResponse response = paymentService.processPayment(packet);

        // Assert
        assertNotNull(response);
        assertEquals("EXPIRED", response.getStatus());
        assertNull(response.getSenderBalance());

        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    public void testProcessPaymentIdempotentReplay() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        Packet packet = createEncryptedPacket(txnId, "alice@mesh", "bob@mesh", new BigDecimal("50.00"), LocalDateTime.now().plusMinutes(10));

        ProcessedPayment storedPayment = ProcessedPayment.builder()
                .transactionId(txnId)
                .status("SUCCESS")
                .processedAt(LocalDateTime.now().minusMinutes(5))
                .senderBalance(new BigDecimal("450.00"))
                .receiverBalance(new BigDecimal("150.00"))
                .checksum(packet.getChecksum())
                .sender("alice@mesh")
                .receiver("bob@mesh")
                .amount(new BigDecimal("50.00"))
                .build();

        when(processedPaymentRepository.findByTransactionId(txnId)).thenReturn(Optional.of(storedPayment));

        // Act
        PaymentResponse response = paymentService.processPayment(packet);

        // Assert
        assertNotNull(response);
        assertEquals("SUCCESS", response.getStatus());
        assertTrue(response.getMessage().contains("Idempotent replay"));
        assertEquals(new BigDecimal("450.00"), response.getSenderBalance());
        assertEquals(new BigDecimal("150.00"), response.getReceiverBalance());

        // Verify database balance updates are not called again
        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    public void testProcessPaymentChecksumFailure() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        Packet packet = createEncryptedPacket(txnId, "alice@mesh", "bob@mesh", new BigDecimal("50.00"), LocalDateTime.now().plusMinutes(10));
        // Tamper checksum signature
        packet.setChecksum("tampered-hash-signature");

        when(processedPaymentRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(SecurityException.class, () -> {
            paymentService.processPayment(packet);
        });

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    public void testProcessPaymentInsufficientBalance() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.00"); // exceeds balance
        Packet packet = createEncryptedPacket(txnId, "alice@mesh", "bob@mesh", amount, LocalDateTime.now().plusMinutes(10));

        Account senderAccount = Account.builder()
                .accountNumber("ACC-111")
                .upiId("alice@mesh")
                .currentBalance(new BigDecimal("500.00"))
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        Account receiverAccount = Account.builder()
                .accountNumber("ACC-222")
                .upiId("bob@mesh")
                .currentBalance(new BigDecimal("100.00"))
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        when(processedPaymentRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(accountRepository.findByUpiId("alice@mesh")).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUpiId("bob@mesh")).thenReturn(Optional.of(receiverAccount));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            paymentService.processPayment(packet);
        });

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    public void testProcessPaymentUnknownAccount() {
        // Arrange
        UUID txnId = UUID.randomUUID();
        Packet packet = createEncryptedPacket(txnId, "alice@mesh", "unknown@mesh", new BigDecimal("50.00"), LocalDateTime.now().plusMinutes(10));

        Account senderAccount = Account.builder()
                .accountNumber("ACC-111")
                .upiId("alice@mesh")
                .currentBalance(new BigDecimal("500.00"))
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        when(processedPaymentRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        when(accountRepository.findByUpiId("alice@mesh")).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByUpiId("unknown@mesh")).thenReturn(Optional.empty()); // missing

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            paymentService.processPayment(packet);
        });

        verify(accountRepository, never()).save(any(Account.class));
    }
}
