package com.innowise.authenticationservice.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RefreshTokenRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.LogoutRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.dto.UpdateUserProfileRequest;
import com.innowise.authenticationservice.exception.AuthenticationException;
import com.innowise.authenticationservice.service.AuthService;

/**
 * REST контроллер для аутентификации и управления токенами.
 * Предоставляет endpoints для регистрации, входа, обновления токенов и валидации.
 */
@RestController
@RequestMapping("/auth/v1")
public class AuthController {

    private final AuthService authService;
    private final String internalApiKey;

    public AuthController(AuthService authService,
                          @Value("${internal.api.key:}") String internalApiKey) {
        this.authService = authService;
        this.internalApiKey = internalApiKey;
    }
    /**
     * Аутентификация пользователя по логину и паролю.
     * Возвращает access и refresh токены при успешной аутентификации.
     * 
     * @param loginRequest данные для входа (login, password)
     * @return TokenResponse с access и refresh токенами
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    /**
     * Регистрация нового пользователя в auth-service.
     * Создает учетные данные в auth_db.
     * После регистрации необходимо войти через /auth/v1/login для получения токенов.
     * 
     * @param registerRequest данные для регистрации (login, password, role)
     * @return ResponseEntity со статусом 201 Created
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return ResponseEntity.status(201).body("User registered successfully. Please login to get tokens.");
    }

    /**
     * Обновление access токена с помощью refresh токена.
     * Возвращает новую пару access и refresh токенов.
     * 
     * @param request запрос с refresh токеном
     * @return TokenResponse с новыми access и refresh токенами
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    /**
     * Создание токена для существующего пользователя (алиас для /login).
     * Используется для совместимости со старыми клиентами.
     * 
     * @param loginRequest данные для входа (login, password)
     * @return TokenResponse с access и refresh токенами
     */
    @PostMapping("/create-token")
    public ResponseEntity<TokenResponse> createToken(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

//    @PostMapping("/validate")
//    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody String request) {
//        return ResponseEntity.ok(authService.validateToken(request));
//    }

//    @PostMapping("/validate")
//    public ResponseEntity<TokenValidationResponse> validateToken(
//            @RequestHeader("Authorization") String authHeader) {
//
//        String token = extractTokenFromHeader(authHeader); // "Bearer {token}"
//        TokenValidationResponse validationResponse = authService.validateToken(token);
//        return ResponseEntity.ok(validationResponse);
//    }
//
//    private String extractTokenFromHeader(String authHeader) {
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            return authHeader.substring(7);
//        }
//        throw new RuntimeException("Invalid Authorization header");
//    }

    /**
     * Валидация JWT токена.
     * Проверяет валидность токена и возвращает информацию о пользователе (username, role).
     * Используется другими сервисами для проверки токенов.
     * 
     * @param request запрос с токеном для валидации
     * @return TokenValidationResponse с результатом валидации и информацией о пользователе
     */
    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @Valid @RequestBody TokenValidationRequest request) {
        TokenValidationResponse validationResponse = authService.validateToken(request.getToken());
        return ResponseEntity.ok(validationResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/profile")
    public ResponseEntity<Void> updateUserProfile(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        if (internalApiKey == null || internalApiKey.isBlank() || !internalApiKey.equals(apiKey)) {
            throw new AuthenticationException("Invalid internal API key");
        }
        authService.updateUserProfile(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Удаление пользователя по email (логину) из auth_db и Keycloak
     * Защищено внутренним API ключом
     */
    @DeleteMapping("/users/{email}")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader("X-Internal-Api-Key") String apiKey,
            @PathVariable String email) {
        if (internalApiKey == null || internalApiKey.isBlank() || !internalApiKey.equals(apiKey)) {
            throw new AuthenticationException("Invalid internal API key");
        }
        authService.deleteUser(email);
        return ResponseEntity.noContent().build();
    }
}
