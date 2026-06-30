package com.meshpay.bankservice.config;

import com.meshpay.common.crypto.RsaUtility;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;

@Configuration
@Slf4j
public class SecurityKeyPairConfig {

    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        log.info("Encryption started: Generating Bank security key pair...");
        this.keyPair = RsaUtility.generateKeyPair();
        log.info("Encryption completed: Bank security key pair initialized.");
    }

    @Bean
    public KeyPair bankKeyPair() {
        return this.keyPair;
    }
}
