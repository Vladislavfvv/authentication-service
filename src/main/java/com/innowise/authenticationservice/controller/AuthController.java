package com.innowise.authenticationservice.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RefreshTokenRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.service.AuthService;

@RestController
@RequestMapping("/auth/v1")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
     * Создает учетные данные в auth_db и возвращает JWT токены.
     * Пользователь должен самостоятельно вызвать user-service для создания профиля
     * через POST /api/v1/users/self с полученным токеном.
     *
     * @param registerRequest данные для регистрации (login, password, role)
     * @return TokenResponse с access и refresh токенами
     */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        TokenResponse tokenResponse = authService.register(registerRequest);
        return ResponseEntity.ok(tokenResponse);
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
}
