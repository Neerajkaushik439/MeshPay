package com.meshpay.bankservice.repository;

import com.meshpay.bankservice.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findAllByTransactionId(UUID transactionId);
    List<LedgerEntry> findAllByAccountNumber(String accountNumber);
}
