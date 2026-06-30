package com.meshpay.transactionservice.controller;

import com.meshpay.common.dto.UserDto;
import com.meshpay.transactionservice.dto.CreateTransactionRequest;
import com.meshpay.transactionservice.dto.TransactionResponse;
import com.meshpay.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            @AuthenticationPrincipal UserDto sender
    ) {
        TransactionResponse response = transactionService.createTransaction(request, sender);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getMyTransactions(
            @AuthenticationPrincipal UserDto sender
    ) {
        List<TransactionResponse> responses = transactionService.getSenderTransactions(sender.getEmail());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionDetails(
            @PathVariable UUID transactionId
    ) {
        TransactionResponse response = transactionService.getTransactionByUuid(transactionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{transactionId}/events")
    public ResponseEntity<List<com.meshpay.common.dto.TransactionEvent>> getTransactionEvents(
            @PathVariable UUID transactionId
    ) {
        List<com.meshpay.common.dto.TransactionEvent> events = transactionService.getTransactionEvents(transactionId);
        return ResponseEntity.ok(events);
    }

    @PostMapping("/packet-status")
    public ResponseEntity<Void> updatePacketStatus(@RequestBody com.meshpay.common.dto.Packet packet) {
        transactionService.updatePacketStatus(packet);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/events")
    public ResponseEntity<Void> receiveTransactionEvent(@RequestBody com.meshpay.common.dto.TransactionEvent event) {
        transactionService.processTransactionEvent(event);
        return ResponseEntity.ok().build();
    }
}
