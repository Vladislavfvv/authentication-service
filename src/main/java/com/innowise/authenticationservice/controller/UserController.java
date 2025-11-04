package com.innowise.authenticationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.authenticationservice.dto.TokenValidationResponse;

@RestController
@RequestMapping("/user")
public class UserController {
    @PostMapping("/register")
    public ResponseEntity registerUser(@RequestBody UserRegistrationRequest request) {
        // 1. Отправляем в Auth Service создать учетные данные
        restTemplate.postForObject(
                "http://authentication-service:8081/auth/register",
                new AuthRegisterRequest(request.getLogin(), request.getPassword()),
                Void.class
        );

        // 2. Создаем профиль в User Service
        UserProfile profile = new UserProfile(request.getLogin(), request.getEmail(), ...);
        userRepository.save(profile);

        return ResponseEntity.ok().build();
    }

    // User Service
    @GetMapping("/users/{userId}")
    public ResponseEntity getUserProfile(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader) {

        // Валидируем токен через Auth Service
        String token = authHeader.replace("Bearer ", "");
        TokenValidationResponse validation = restTemplate.postForObject(
                "http://authentication-service:8081/auth/validate?token=" + token,
                null, TokenValidationResponse.class
        );

        if (!validation.isValid()) {
            throw new SecurityException("Invalid token");
        }

        // Проверяем права доступа
        if (!validation.getUsername().equals(getUserLogin(userId)) &&
            !validation.getRole().equals("ROLE_ADMIN")) {
            throw new SecurityException("Access denied");
        }

        return ResponseEntity.ok(userService.getUserProfile(userId));
    }
}
