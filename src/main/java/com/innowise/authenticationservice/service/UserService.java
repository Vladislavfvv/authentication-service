package com.innowise.authenticationservice.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.innowise.authenticationservice.dto.TokenValidationResponse;

public class UserService {
    public UserService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private final RestTemplate restTemplate;
//Валидация доступа пользователя к ресурсу
    public boolean validateUserAccess(String authHeader, String requiredLogin) {
        try {
            // Вызов Auth Service через header
            ResponseEntity<TokenValidationResponse> response = restTemplate.exchange(
                    "http://authentication-service:8081/auth/validate",
                    HttpMethod.POST,
                    new HttpEntity<>(null, createHeaders(authHeader)),
                    TokenValidationResponse.class
            );

            TokenValidationResponse validation = response.getBody();
            
            return validation != null &&
                   validation.isValid() &&
                   validation.getUsername().equals(requiredLogin);

        } catch (Exception e) {
            return false;
        }
    }

    private HttpHeaders createHeaders(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return headers;
    }
}
