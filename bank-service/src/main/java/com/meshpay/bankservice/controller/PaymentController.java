package com.meshpay.bankservice.controller;

import com.meshpay.bankservice.dto.PaymentResponse;
import com.meshpay.bankservice.service.PaymentService;
import com.meshpay.common.dto.Packet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody Packet packet) {
        PaymentResponse response = paymentService.processPayment(packet);
        return ResponseEntity.ok(response);
    }
}
