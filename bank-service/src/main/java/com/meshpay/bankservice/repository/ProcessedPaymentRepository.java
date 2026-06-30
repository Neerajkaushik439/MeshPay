package com.meshpay.bankservice.repository;

import com.meshpay.bankservice.entity.ProcessedPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedPaymentRepository extends JpaRepository<ProcessedPayment, Long> {
    Optional<ProcessedPayment> findByTransactionId(UUID transactionId);
}
