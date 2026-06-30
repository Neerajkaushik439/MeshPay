package com.meshpay.bankservice.controller;

import com.meshpay.bankservice.dto.TransactionRequest;
import com.meshpay.bankservice.dto.TransactionResponse;
import com.meshpay.bankservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.createTransaction(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionByUuid(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(transactionService.getTransactionByUuid(transactionId));
    }
}
