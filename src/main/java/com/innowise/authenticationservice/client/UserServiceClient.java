package com.innowise.authenticationservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public UserServiceClient(RestTemplate restTemplate,
                             @Value("${user.service.base-url:http://localhost:8082}") String baseUrl,
                             @Value("${user.service.internal-api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
        log.info("UserServiceClient initialized. Base URL: {}, API Key configured: {}", 
                baseUrl, internalApiKey != null && !internalApiKey.isBlank());
    }

    /**
     * Создание пользователя в user-service при регистрации
     */
    public void createUser(String email, String firstName, String lastName) {
        if (baseUrl == null || baseUrl.isBlank() || internalApiKey == null || internalApiKey.isBlank()) {
            log.warn("User service URL or internal API key not configured. Skipping user creation in user-service.");
            return;
        }

        try {
            UserDto userDto = new UserDto();
            userDto.setEmail(email);
            userDto.setFirstName(firstName != null && !firstName.isBlank() ? firstName : "Unknown");
            userDto.setLastName(lastName != null && !lastName.isBlank() ? lastName : "Unknown");
            // Устанавливаем дату рождения по умолчанию (можно будет обновить позже)
            userDto.setBirthDate(java.time.LocalDate.now().minusYears(18));

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);

            HttpEntity<UserDto> entity = new HttpEntity<>(userDto, headers);

            ResponseEntity<UserDto> response = restTemplate.exchange(
                    baseUrl + "/api/v1/users/sync",
                    HttpMethod.POST,
                    entity,
                    UserDto.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully created user {} in user-service", email);
            } else {
                log.warn("Failed to create user {} in user-service. Status: {}", email, response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Failed to create user {} in user-service: {}", email, e.getMessage(), e);
            // Не выбрасываем исключение, чтобы не прерывать регистрацию в authentication-service
        }
    }

    // DTO для создания пользователя в user-service
    public static class UserDto {
        private String email;
        private String firstName;
        private String lastName;
        private java.time.LocalDate birthDate;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public java.time.LocalDate getBirthDate() {
            return birthDate;
        }

        public void setBirthDate(java.time.LocalDate birthDate) {
            this.birthDate = birthDate;
        }
    }
}

