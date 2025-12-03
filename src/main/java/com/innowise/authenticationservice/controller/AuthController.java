package com.innowise.authenticationservice.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RefreshTokenRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
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
     * Создает учетные данные в auth_db и сразу выдает JWT токены.
     * После регистрации пользователь должен самостоятельно создать профиль в user-service,
     * используя полученный токен (email будет извлечен из токена автоматически).
     * 
     * @param registerRequest данные для регистрации (login, password, role)
     * @return TokenResponse с access и refresh токенами
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        TokenResponse tokenResponse = authService.register(registerRequest);
        return ResponseEntity.status(201).body(tokenResponse);
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

    /**
     * Внутренний технический endpoint для синхронизации удаления пользователя из auth_db.
     * Используется только user-service при удалении пользователя для синхронизации данных между сервисами.
     * НЕ предназначен для прямого использования клиентами.
     * Требует внутренний API ключ для безопасности.
     * 
     * @param email email (login) пользователя для удаления
     * @return ResponseEntity со статусом 204 No Content при успешном удалении
     */
    @DeleteMapping("/internal/sync/users/{email}")
    public ResponseEntity<Void> deleteUserByEmail(
            @PathVariable String email,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);
        
        log.info("Received DELETE request for user with email: {} (encoded: {})", email, email);
        log.info("Internal API key configuration - configured: {}, received: {}", 
                internalApiKey != null && !internalApiKey.isBlank(), 
                apiKey != null && !apiKey.isBlank());
        
        // Проверка внутреннего API ключа для безопасности
        // Если API ключ настроен, проверяем его наличие и корректность
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            if (apiKey == null || apiKey.isBlank() || !apiKey.equals(internalApiKey)) {
                log.warn("Unauthorized attempt to delete user. Invalid or missing internal API key. Expected: {}, Received: {}", 
                        internalApiKey, apiKey);
                throw new AuthenticationException("Invalid or missing internal API key");
            }
            log.info("Internal API key validated successfully");
        } else {
            log.warn("Internal API key not configured. Endpoint is accessible without authentication.");
        }
        
        // Декодируем email, если он был URL-закодирован
        String originalEmail = email;
        try {
            email = java.net.URLDecoder.decode(email, java.nio.charset.StandardCharsets.UTF_8);
            if (!originalEmail.equals(email)) {
                log.info("Decoded email from {} to {}", originalEmail, email);
            }
        } catch (Exception e) {
            log.warn("Failed to decode email, using original value: {}", email);
        }
        
        log.info("Deleting user with email: {} from auth_db", email);
        
        try {
            authService.deleteUserByEmail(email);
            log.info("Successfully deleted user with email: {} from auth_db", email);
        } catch (Exception e) {
            log.error("Failed to delete user with email: {} from auth_db: {}", email, e.getMessage(), e);
            throw e;
        }
        
        return ResponseEntity.noContent().build();
    }

}
