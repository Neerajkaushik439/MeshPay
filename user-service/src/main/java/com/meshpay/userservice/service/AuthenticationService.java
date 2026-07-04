package com.meshpay.userservice.service;

import com.meshpay.common.dto.AuthResponse;
import com.meshpay.common.dto.LoginRequest;
import com.meshpay.common.dto.RegisterRequest;
import com.meshpay.userservice.entity.User;
import com.meshpay.userservice.repository.UserRepository;
import com.meshpay.userservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${services.bank-service.url:http://localhost:8085}")
    private String bankServiceUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email address already exists.");
        }

        // Generate a unique UPI ID from the user's full name
        String upiId = generateUniqueUpiId(request.getFullName());

        // Determine initial balance (default to 0 if not provided)
        BigDecimal initialBalance = request.getInitialBalance() != null
                ? request.getInitialBalance()
                : BigDecimal.ZERO;

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .upiId(upiId)
                .build();

        User savedUser = userRepository.save(user);

        // Create corresponding bank account via Bank Service
        createBankAccount(savedUser.getFullName(), upiId, initialBalance);

        String jwtToken = jwtService.generateToken(savedUser);

        return AuthResponse.builder()
                .token(jwtToken)
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .upiId(savedUser.getUpiId())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .fullName(user.getFullName())
                .upiId(user.getUpiId())
                .build();
    }

    /**
     * Generates a unique UPI ID from the user's full name.
     * Format: sanitized_name@meshpay (e.g. alice@meshpay)
     * Appends a numeric suffix if the base ID already exists.
     */
    private String generateUniqueUpiId(String fullName) {
        // Sanitize: lowercase, remove non-alphanumeric except dots/hyphens/underscores, collapse spaces
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

        // Append numeric suffix to ensure uniqueness
        int suffix = 1;
        String candidateUpiId;
        do {
            candidateUpiId = sanitized + suffix + "@meshpay";
            suffix++;
        } while (userRepository.existsByUpiId(candidateUpiId));

        return candidateUpiId;
    }

    /**
     * Calls the Bank Service to create a bank account for the newly registered user.
     */
    private void createBankAccount(String accountHolderName, String upiId, BigDecimal initialBalance) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            Map<String, Object> accountRequest = new HashMap<>();
            accountRequest.put("accountNumber", "ACC-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            accountRequest.put("accountHolderName", accountHolderName);
            accountRequest.put("upiId", upiId);
            accountRequest.put("currentBalance", initialBalance);
            accountRequest.put("accountStatus", "ACTIVE");

            String url = bankServiceUrl + "/api/accounts";
            log.info("Creating bank account for UPI ID {} at {}", upiId, url);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, accountRequest, Map.class);
            log.info("Bank account created successfully for UPI ID {}: {}", upiId, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to create bank account for UPI ID {}: {}", upiId, e.getMessage());
            throw new RuntimeException("Registration succeeded but bank account creation failed. Please contact support.", e);
        }
    }
}
