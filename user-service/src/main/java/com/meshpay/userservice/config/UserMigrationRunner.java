package com.meshpay.userservice.config;

import com.meshpay.userservice.entity.User;
import com.meshpay.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Startup migration that generates UPI IDs for any existing users
 * that were created before the upiId column was added.
 * Also creates bank accounts for those users if they don't exist yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserMigrationRunner implements CommandLineRunner {

    private final UserRepository userRepository;

    @Value("${services.bank-service.url:http://localhost:8085}")
    private String bankServiceUrl;

    @Override
    public void run(String... args) {
        List<User> usersWithoutUpi = userRepository.findAll().stream()
                .filter(u -> u.getUpiId() == null || u.getUpiId().isBlank())
                .toList();

        if (usersWithoutUpi.isEmpty()) {
            log.info("[MIGRATION] All users have UPI IDs assigned. No migration needed.");
            return;
        }

        log.info("[MIGRATION] Found {} users without UPI IDs. Starting migration...", usersWithoutUpi.size());

        for (User user : usersWithoutUpi) {
            try {
                String upiId = generateUniqueUpiId(user.getFullName());
                user.setUpiId(upiId);
                userRepository.save(user);
                log.info("[MIGRATION] Assigned UPI ID '{}' to user '{}'", upiId, user.getEmail());

                // Create bank account with zero balance for existing users
                createBankAccountIfNotExists(user.getFullName(), upiId);
            } catch (Exception e) {
                log.error("[MIGRATION] Failed to migrate user '{}': {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("[MIGRATION] Migration complete.");
    }

    private String generateUniqueUpiId(String fullName) {
        String sanitized = fullName.toLowerCase()
                .replaceAll("[^a-z0-9._\\-]", "")
                .replaceAll("\\s+", "");

        if (sanitized.isEmpty()) {
            sanitized = "user";
        }

        String baseUpiId = sanitized + "@meshpay";

        if (!userRepository.existsByUpiId(baseUpiId)) {
            return baseUpiId;
        }

        int suffix = 1;
        String candidateUpiId;
        do {
            candidateUpiId = sanitized + suffix + "@meshpay";
            suffix++;
        } while (userRepository.existsByUpiId(candidateUpiId));

        return candidateUpiId;
    }

    private void createBankAccountIfNotExists(String accountHolderName, String upiId) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // First check if account already exists
            try {
                restTemplate.getForEntity(bankServiceUrl + "/api/accounts/upi/" + upiId, Map.class);
                log.info("[MIGRATION] Bank account already exists for UPI ID '{}'", upiId);
                return;
            } catch (Exception ignored) {
                // Account doesn't exist, create it
            }

            Map<String, Object> accountRequest = new HashMap<>();
            accountRequest.put("accountNumber", "ACC-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            accountRequest.put("accountHolderName", accountHolderName);
            accountRequest.put("upiId", upiId);
            accountRequest.put("currentBalance", BigDecimal.ZERO);
            accountRequest.put("accountStatus", "ACTIVE");

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    bankServiceUrl + "/api/accounts", accountRequest, Map.class);
            log.info("[MIGRATION] Created bank account for UPI ID '{}': {}", upiId, response.getStatusCode());
        } catch (Exception e) {
            log.warn("[MIGRATION] Could not create bank account for UPI ID '{}': {}. " +
                    "It will be created on first transaction.", upiId, e.getMessage());
        }
    }
}
