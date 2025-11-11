package com.innowise.authenticationservice.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
//прокси-клиент для проверки прав доступа через удалённый authentication-service
public class UserService {
    
    private final RestTemplate restTemplate;

    public UserService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

//Валидация доступа пользователя к ресурсу ожидает заголовок Authorization и логин, чей доступ нужно подтвердить
    public boolean validateUserAccess(String authHeader, String requiredLogin) {
        try {
            // Вызов Auth Service  передаёт заголовок дальше и анализирует ответ (TokenValidationResponse): если сервис вернул валидный токен и логин из токена совпал с требуемым, метод возвращает true, иначе — false
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
            return false;//Исключения при обмене это запрет доступа
        }
    }

    //создание заголовка - обёртка над установкой Authorization в HttpHeaders
    private HttpHeaders createHeaders(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return headers;
    }
}
