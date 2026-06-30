package com.meshpay.transactionservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class BankClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.bank-service.url}")
    private String bankServiceUrl;

    public PublicKey getBankPublicKey() {
        String url = bankServiceUrl + "/api/security/public-key";
        try {
            String base64PublicKey = restTemplate.getForObject(url, String.class);
            if (base64PublicKey == null || base64PublicKey.trim().isEmpty()) {
                throw new RuntimeException("Received empty public key from Bank service");
            }
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey.trim());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Bank RSA public key from Bank service", e);
        }
    }
}
