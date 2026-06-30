package com.meshpay.transactionservice.service;

import com.meshpay.common.crypto.RsaUtility;
import com.meshpay.common.dto.UserDto;
import com.meshpay.common.dto.Packet;
import com.meshpay.common.dto.PacketStatus;
import com.meshpay.transactionservice.client.BankClient;
import com.meshpay.transactionservice.dto.CreateTransactionRequest;
import com.meshpay.transactionservice.dto.TransactionResponse;
import com.meshpay.transactionservice.entity.Transaction;
import com.meshpay.transactionservice.entity.TransactionStatus;
import com.meshpay.transactionservice.exception.DuplicateResourceException;
import com.meshpay.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BankClient bankClient;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TransactionService transactionService;

    private KeyPair keyPair;
    private UserDto sender;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        keyPair = RsaUtility.generateKeyPair();
        sender = UserDto.builder()
                .id(1L)
                .fullName("Alice Jenkins")
                .email("alice@meshpay.com")
                .build();
        ReflectionTestUtils.setField(transactionService, "nextNodeUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(transactionService, "restTemplate", restTemplate);
    }

    @Test
    public void testCreateTransactionSuccess() {
        // Arrange
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .receiverUpiId("bob@mesh")
                .amount(new BigDecimal("100.00"))
                .build();

        Packet ackPacket = Packet.builder()
                .packetStatus(PacketStatus.FORWARDED)
                .build();

        when(bankClient.getBankPublicKey()).thenReturn(keyPair.getPublic());
        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(restTemplate.postForEntity(anyString(), any(Packet.class), eq(Packet.class)))
                .thenReturn(new ResponseEntity<>(ackPacket, HttpStatus.OK));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setId(10L);
            txn.setCreatedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            return txn;
        });

        // Act
        TransactionResponse response = transactionService.createTransaction(request, sender);

        // Assert
        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("alice@mesh", response.getSenderUpiId());
        assertEquals("bob@mesh", response.getReceiverUpiId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals(TransactionStatus.ROUTING, response.getStatus()); // Status becomes ROUTING on routing success
        assertTrue(response.isEncrypted());
        assertNotNull(response.getTransactionId());
        assertNotNull(response.getIdempotencyKey());
        assertNotNull(response.getChecksum());
        assertNotNull(response.getEncryptedData());
        assertNotNull(response.getEncryptedKey());
        assertNotNull(response.getIv());

        // Verify TTL is roughly 60 mins in future
        assertNotNull(response.getTtl());
        assertTrue(response.getTtl().isAfter(LocalDateTime.now().plusMinutes(59)));
        assertTrue(response.getTtl().isBefore(LocalDateTime.now().plusMinutes(61)));

        verify(bankClient, times(1)).getBankPublicKey();
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
        verify(restTemplate, times(1)).postForEntity(anyString(), any(Packet.class), eq(Packet.class));
    }

    @Test
    public void testCreateTransactionRoutingFailed() {
        // Arrange
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .receiverUpiId("bob@mesh")
                .amount(new BigDecimal("100.00"))
                .build();

        when(bankClient.getBankPublicKey()).thenReturn(keyPair.getPublic());
        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(restTemplate.postForEntity(anyString(), any(Packet.class), eq(Packet.class)))
                .thenThrow(new RuntimeException("Relay Connection Failure"));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction txn = invocation.getArgument(0);
            txn.setId(10L);
            txn.setCreatedAt(LocalDateTime.now());
            txn.setUpdatedAt(LocalDateTime.now());
            return txn;
        });

        // Act
        TransactionResponse response = transactionService.createTransaction(request, sender);

        // Assert
        assertNotNull(response);
        assertEquals(TransactionStatus.FAILED, response.getStatus()); // Status becomes FAILED on routing failure
        verify(restTemplate, times(3)).postForEntity(anyString(), any(Packet.class), eq(Packet.class));
    }

    @Test
    public void testSelfTransferRejected() {
        // Arrange
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .receiverUpiId("alice@mesh") // Derived from sender email: alice@meshpay.com -> alice@mesh
                .amount(new BigDecimal("50.00"))
                .build();

        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            transactionService.createTransaction(request, sender);
        });

        assertEquals("Self-transfer is not allowed", exception.getMessage());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    public void testDuplicateIdempotencyRejected() {
        // Arrange
        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .receiverUpiId("bob@mesh")
                .amount(new BigDecimal("100.00"))
                .build();

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(DuplicateResourceException.class, () -> {
            transactionService.createTransaction(request, sender);
        });

        verify(transactionRepository, never()).save(any(Transaction.class));
    }
}
