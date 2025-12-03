package com.innowise.authenticationservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.exception.AuthenticationException;
import com.innowise.authenticationservice.model.Role;
import com.innowise.authenticationservice.model.User;
import com.innowise.authenticationservice.repository.UserRepository;
import com.innowise.authenticationservice.service.AuthService;
import com.innowise.authenticationservice.security.JwtTokenProvider;

/**
 * Интеграционные тесты для AuthService.
 * Тестируют работу сервиса с реальной базой данных PostgreSQL через Testcontainers.
 */
// @DirtiesContext - помечает Spring контекст как "грязный" после каждого тестового метода.
// Это гарантирует, что Spring пересоздаст контекст приложения перед следующим тестом,
// обеспечивая полную изоляцию тестов друг от друга. Необходимо для интеграционных тестов,
// которые могут изменять состояние базы данных или Spring бинов, чтобы избежать побочных эффектов.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthServiceIT extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // Подготавливаем данные для регистрации
        registerRequest = new RegisterRequest();
        registerRequest.setLogin("testuser@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setRole("USER");

        // Подготавливаем данные для входа
        loginRequest = new LoginRequest();
        loginRequest.setLogin("testuser@example.com");
        loginRequest.setPassword("password123");
    }

    @Test
    @Order(1)
    @DisplayName("register - успешная регистрация пользователя")
    void register_ShouldSaveUser() {
        // given
        // registerRequest уже инициализирован в @BeforeEach

        // when
        // Вызываем метод register сервиса, который должен сохранить пользователя в реальной БД
        authService.register(registerRequest);

        // then
        // Проверяем, что пользователь был сохранен в БД
        assertTrue(userRepository.existsByLogin("testuser@example.com"));
        User savedUser = userRepository.findByLogin("testuser@example.com").orElse(null);
        assertNotNull(savedUser);
        assertEquals("testuser@example.com", savedUser.getLogin());
        assertEquals(Role.ROLE_USER, savedUser.getRole());
        assertNotNull(savedUser.getPasswordHash());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    @Order(2)
    @DisplayName("register - ошибка при регистрации с существующим login")
    void register_ShouldThrowException_WhenLoginExists() {
        // given
        // Регистрируем первого пользователя
        authService.register(registerRequest);

        // when & then
        // Пытаемся зарегистрировать пользователя с тем же login - должна быть ошибка
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(registerRequest);
        });
        assertEquals("Login already exists", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("register - ошибка при попытке зарегистрировать ADMIN")
    void register_ShouldThrowException_WhenRoleIsAdmin() {
        // given
        registerRequest.setRole("ADMIN");

        // when & then
        // Пытаемся зарегистрировать пользователя с ролью ADMIN - должна быть ошибка
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(registerRequest);
        });
        assertEquals("Cannot register with ADMIN role", exception.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("login - успешный вход пользователя")
    void login_ShouldReturnTokens_WhenCredentialsAreValid() {
        // given
        // Регистрируем пользователя перед входом
        authService.register(registerRequest);

        // when
        // Вызываем метод login сервиса с валидными учетными данными
        TokenResponse response = authService.login(loginRequest);

        // then
        // Проверяем, что получены access и refresh токены
        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertTrue(response.getExpiresIn() > 0);
        
        // Проверяем, что токены валидны
        assertTrue(jwtTokenProvider.validateToken(response.getAccessToken()));
        assertTrue(jwtTokenProvider.validateToken(response.getRefreshToken()));
        
        // Проверяем содержимое токенов
        assertEquals("testuser@example.com", jwtTokenProvider.getUsernameFromToken(response.getAccessToken()));
        assertEquals("ROLE_USER", jwtTokenProvider.getRoleFromToken(response.getAccessToken()));
    }

    @Test
    @Order(5)
    @DisplayName("login - ошибка при неверном login")
    void login_ShouldThrowException_WhenLoginIsInvalid() {
        // given
        loginRequest.setLogin("nonexistent@example.com");

        // when & then
        // Пытаемся войти с несуществующим login - должна быть ошибка
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login(loginRequest);
        });
        assertEquals("Invalid login or password", exception.getMessage());
    }

    @Test
    @Order(6)
    @DisplayName("login - ошибка при неверном password")
    void login_ShouldThrowException_WhenPasswordIsInvalid() {
        // given
        authService.register(registerRequest);
        loginRequest.setPassword("wrongpassword");

        // when & then
        // Пытаемся войти с неверным паролем - должна быть ошибка
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login(loginRequest);
        });
        assertEquals("Invalid login or password", exception.getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("refreshToken - успешное обновление токена")
    void refreshToken_ShouldReturnNewTokens_WhenRefreshTokenIsValid() {
        // given
        // Регистрируем пользователя и получаем токены
        authService.register(registerRequest);
        TokenResponse initialTokens = authService.login(loginRequest);

        // when
        // Вызываем метод refreshToken с валидным refresh токеном
        TokenResponse newTokens = authService.refreshToken(initialTokens.getRefreshToken());

        // then
        // Проверяем, что получены новые токены
        assertNotNull(newTokens);
        assertNotNull(newTokens.getAccessToken());
        assertNotNull(newTokens.getRefreshToken());
        
        // Проверяем, что новые токены валидны
        assertTrue(jwtTokenProvider.validateToken(newTokens.getAccessToken()));
        assertTrue(jwtTokenProvider.validateToken(newTokens.getRefreshToken()));
        
        // Проверяем содержимое новых токенов
        assertEquals("testuser@example.com", jwtTokenProvider.getUsernameFromToken(newTokens.getAccessToken()));
        assertEquals("ROLE_USER", jwtTokenProvider.getRoleFromToken(newTokens.getAccessToken()));
    }

    @Test
    @Order(8)
    @DisplayName("refreshToken - ошибка при невалидном refresh токене")
    void refreshToken_ShouldThrowException_WhenRefreshTokenIsInvalid() {
        // given
        String invalidToken = "invalid.refresh.token";

        // when & then
        // Пытаемся обновить токен с невалидным refresh токеном - должна быть ошибка
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.refreshToken(invalidToken);
        });
        assertEquals("Invalid refresh token", exception.getMessage());
    }

    @Test
    @Order(9)
    @DisplayName("validateToken - успешная валидация валидного токена")
    void validateToken_ShouldReturnTrue_WhenTokenIsValid() {
        // given
        // Регистрируем пользователя и получаем токен
        authService.register(registerRequest);
        TokenResponse tokens = authService.login(loginRequest);

        // when
        // Вызываем метод validateToken с валидным токеном
        TokenValidationResponse response = authService.validateToken(tokens.getAccessToken());

        // then
        // Проверяем, что токен валиден
        assertNotNull(response);
        assertTrue(response.isValid());
        assertEquals("testuser@example.com", response.getUsername());
        assertEquals("ROLE_USER", response.getRole());
    }

    @Test
    @Order(10)
    @DisplayName("validateToken - возвращает false для невалидного токена")
    void validateToken_ShouldReturnFalse_WhenTokenIsInvalid() {
        // given
        String invalidToken = "invalid.token.here";

        // when
        // Вызываем метод validateToken с невалидным токеном
        TokenValidationResponse response = authService.validateToken(invalidToken);

        // then
        // Проверяем, что токен невалиден
        assertNotNull(response);
        assertFalse(response.isValid());
        assertNull(response.getUsername());
        assertNull(response.getRole());
    }
}

