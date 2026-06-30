package com.meshpay.bankservice.service;

import com.meshpay.bankservice.dto.TransactionRequest;
import com.meshpay.bankservice.dto.TransactionResponse;
import com.meshpay.bankservice.entity.Transaction;
import com.meshpay.bankservice.entity.TransactionStatus;
import com.meshpay.bankservice.exception.ResourceNotFoundException;
import com.meshpay.bankservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        Transaction transaction = Transaction.builder()
                .transactionId(UUID.randomUUID())
                .senderUpiId(request.getSenderUpiId())
                .receiverUpiId(request.getReceiverUpiId())
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        return transactionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByUuid(UUID transactionId) {
        Transaction transaction = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with UUID: " + transactionId));
        return mapToResponse(transaction);
    }

    private TransactionResponse mapToResponse(Transaction txn) {
        return TransactionResponse.builder()
                .id(txn.getId())
                .transactionId(txn.getTransactionId())
                .senderUpiId(txn.getSenderUpiId())
                .receiverUpiId(txn.getReceiverUpiId())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .createdAt(txn.getCreatedAt())
                .updatedAt(txn.getUpdatedAt())
                .build();
    }
}
