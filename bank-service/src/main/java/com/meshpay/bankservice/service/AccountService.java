package com.meshpay.bankservice.service;

import com.meshpay.bankservice.dto.AccountRequest;
import com.meshpay.bankservice.dto.AccountResponse;
import com.meshpay.bankservice.entity.Account;
import com.meshpay.bankservice.exception.DuplicateResourceException;
import com.meshpay.bankservice.exception.ResourceNotFoundException;
import com.meshpay.bankservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new DuplicateResourceException("Account number already exists: " + request.getAccountNumber());
        }
        if (accountRepository.existsByUpiId(request.getUpiId())) {
            throw new DuplicateResourceException("UPI ID already exists: " + request.getUpiId());
        }

        Account account = Account.builder()
                .accountNumber(request.getAccountNumber())
                .accountHolderName(request.getAccountHolderName())
                .upiId(request.getUpiId())
                .currentBalance(request.getCurrentBalance())
                .accountStatus(request.getAccountStatus())
                .build();

        Account saved = accountRepository.save(account);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
        return mapToResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByUpiId(String upiId) {
        Account account = accountRepository.findByUpiId(upiId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with UPI ID: " + upiId));
        return mapToResponse(account);
    }

    @Transactional
    public AccountResponse updateAccount(Long id, AccountRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));

        // Check if updating to an existing account number owned by a different user
        if (!account.getAccountNumber().equals(request.getAccountNumber()) &&
                accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new DuplicateResourceException("Account number already exists: " + request.getAccountNumber());
        }

        // Check if updating to an existing UPI ID owned by a different user
        if (!account.getUpiId().equals(request.getUpiId()) &&
                accountRepository.existsByUpiId(request.getUpiId())) {
            throw new DuplicateResourceException("UPI ID already exists: " + request.getUpiId());
        }

        account.setAccountNumber(request.getAccountNumber());
        account.setAccountHolderName(request.getAccountHolderName());
        account.setUpiId(request.getUpiId());
        account.setCurrentBalance(request.getCurrentBalance());
        account.setAccountStatus(request.getAccountStatus());

        Account updated = accountRepository.save(account);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + id));
        accountRepository.delete(account);
    }

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getAccountHolderName())
                .upiId(account.getUpiId())
                .currentBalance(account.getCurrentBalance())
                .accountStatus(account.getAccountStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
