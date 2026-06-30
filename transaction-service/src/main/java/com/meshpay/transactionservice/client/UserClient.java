package com.meshpay.transactionservice.client;

import com.meshpay.common.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class UserClient {

    private final RestTemplate restTemplate = new RestTemplate();

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
            return null;
        }
    }
}
