package com.meshpay.transactionservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meshpay.common.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {

    private final RestTemplate restTemplate;

    public UserClient() {
        // Configure RestTemplate with JavaTimeModule for proper UserDto LocalDateTime serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        this.restTemplate.getMessageConverters().add(0, converter);
    }

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    public UserDto getAuthenticatedUser(String jwtToken) {
        String url = userServiceUrl + "/api/auth/me";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<UserDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    UserDto.class
            );
            return response.getBody();
        } catch (Exception e) {
            System.out.println("[TXN-SERVICE] USER-CLIENT ME CALL FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("[TXN-SERVICE] USER-CLIENT ROOT CAUSE: " + e.getCause().getClass().getName() + " - " + e.getCause().getMessage());
            }
            return null;
        }
    }
}
