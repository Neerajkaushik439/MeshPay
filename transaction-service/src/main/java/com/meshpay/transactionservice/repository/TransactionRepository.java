package com.meshpay.transactionservice.repository;

import com.meshpay.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(UUID transactionId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.transactionId = :transactionId")
    Optional<Transaction> findByTransactionIdForUpdate(@Param("transactionId") UUID transactionId);

    List<Transaction> findAllBySenderEmail(String senderEmail);
    List<Transaction> findAllBySenderUpiId(String senderUpiId);
    List<Transaction> findAllByReceiverUpiId(String receiverUpiId);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
