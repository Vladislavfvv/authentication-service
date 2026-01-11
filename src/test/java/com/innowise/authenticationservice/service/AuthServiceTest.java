package com.innowise.authenticationservice.service;

import com.innowise.authenticationservice.client.UserServiceClient;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.exception.AuthenticationException;
import com.innowise.authenticationservice.model.Role;
import com.innowise.authenticationservice.model.User;
import com.innowise.authenticationservice.repository.UserRepository;
import com.innowise.authenticationservice.security.JwtTokenProvider;
import com.innowise.authenticationservice.security.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Тесты для AuthService.
 * Проверяет бизнес-логику аутентификации, регистрации и управления токенами.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        // Создаём тестового пользователя для использования в тестах
        // В unit-тестах мы используем моки, поэтому это просто объект с тестовыми данными
        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("testuser");
        testUser.setPasswordHash("$2a$10$hashedPassword"); // Хеш пароля (BCrypt)
        testUser.setRole(Role.ROLE_USER);

        // Создаём DTO для запроса входа
        loginRequest = new LoginRequest();
        loginRequest.setLogin("testuser");
        loginRequest.setPassword("password123");

        // Создаём DTO для запроса регистрации
        registerRequest = new RegisterRequest();
        registerRequest.setLogin("newuser");
        registerRequest.setPassword("password123");
        registerRequest.setRole("USER");
    }

    @Test
    @DisplayName("login - успешный вход с валидными учетными данными")
    void login_ShouldReturnTokens_WhenCredentialsAreValid() {
        // given
        // Когда кто-то вызовет userRepository.findByLogin("testuser"), верни Optional с testUser
        // (это объект, созданный в setUp() с данными пользователя)
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(testUser));
        // Когда кто-то вызовет passwordEncoder.matches("password123", хеш), верни true
        // Это имитирует успешную проверку пароля
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        // Когда кто-то вызовет jwtTokenProvider.generateAccessToken(...), верни "access-token"
        when(jwtTokenProvider.generateAccessToken("testuser", Role.ROLE_USER)).thenReturn("access-token");
        // Когда кто-то вызовет jwtTokenProvider.generateRefreshToken(...), верни "refresh-token"
        when(jwtTokenProvider.generateRefreshToken("testuser", Role.ROLE_USER)).thenReturn("refresh-token");
        // Когда кто-то вызовет jwtTokenProvider.getJwtExpiration(), верни 900000L (время жизни токена)
        when(jwtTokenProvider.getJwtExpiration()).thenReturn(900000L);

        //when
        // Вызываем тестируемый метод входа пользователя
        TokenResponse response = authService.login(loginRequest);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertEquals("access-token", response.getAccessToken()); // Проверка: что access токен совпадает
        assertEquals("refresh-token", response.getRefreshToken()); // Проверка: что refresh токен совпадает
        assertEquals(900000L, response.getExpiresIn()); // Проверка: что время жизни токена совпадает
        verify(userRepository).findByLogin("testuser"); // Проверка: что метод был вызван
        verify(passwordEncoder).matches("password123", "$2a$10$hashedPassword"); // Проверка: что пароль был проверен
        verify(jwtTokenProvider).generateAccessToken("testuser", Role.ROLE_USER); // Проверка: что access токен был сгенерирован
        verify(jwtTokenProvider).generateRefreshToken("testuser", Role.ROLE_USER); // Проверка: что refresh токен был сгенерирован
    }

    @Test
    @DisplayName("login - пользователь не найден")
    void login_ShouldThrowException_WhenUserNotFound() {
        // given & when
        // Когда кто-то вызовет userRepository.findByLogin("testuser"), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким логином не существует в базе данных
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.empty());

        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.login(loginRequest));

        // then
        assertEquals("Invalid login or password", exception.getMessage()); // Проверка: что сообщение исключения совпадает
        verify(userRepository).findByLogin("testuser"); // Проверка: что метод был вызван
        verify(passwordEncoder, never()).matches(anyString(), anyString()); // Проверка: что проверка пароля НЕ была вызвана
        // (если пользователь не найден, пароль проверять не нужно)
    }

    @Test
    @DisplayName("login - неверный пароль")
    void login_ShouldThrowException_WhenPasswordIsInvalid() {
        // given
        // Когда кто-то вызовет userRepository.findByLogin("testuser"), верни Optional с testUser
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(testUser));
        // Когда кто-то вызовет passwordEncoder.matches("wrongpassword", хеш), верни false
        // Это имитирует ситуацию, когда пароль не совпадает с хешем в базе данных
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashedPassword")).thenReturn(false);

        // Устанавливаем неверный пароль в запросе
        loginRequest.setPassword("wrongpassword");

        //when & then
        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.login(loginRequest));

        assertEquals("Invalid login or password", exception.getMessage()); // Проверка: что сообщение исключения совпадает
        verify(userRepository).findByLogin("testuser"); // Проверка: что метод был вызван
        verify(passwordEncoder).matches("wrongpassword", "$2a$10$hashedPassword"); // Проверка: что пароль был проверен
    }

    @Test
    @DisplayName("register - успешная регистрация пользователя")
    void register_ShouldSaveUser_WhenRegistrationIsSuccessful() {
        // given
        // Когда кто-то вызовет userRepository.existsByLogin("newuser"), верни false
        // Это имитирует ситуацию, когда пользователя с таким логином не существует
        when(userRepository.existsByLogin("newuser")).thenReturn(false);
        // Когда кто-то вызовет passwordEncoder.encode("password123"), верни хеш пароля
        // Это имитирует хеширование пароля перед сохранением в БД
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encodedPassword");
        // Когда кто-то вызовет jwtTokenProvider.generateAccessToken(...), верни "access-token"
        when(jwtTokenProvider.generateAccessToken("newuser", Role.ROLE_USER)).thenReturn("access-token");
        // Когда кто-то вызовет jwtTokenProvider.generateRefreshToken(...), верни "refresh-token"
        when(jwtTokenProvider.generateRefreshToken("newuser", Role.ROLE_USER)).thenReturn("refresh-token");
        // Когда кто-то вызовет jwtTokenProvider.getJwtExpiration(), верни 900000L (время жизни токена)
        when(jwtTokenProvider.getJwtExpiration()).thenReturn(900000L);
        // Мокируем вызов userServiceClient.createUser (не выбрасывает исключение)
        doNothing().when(userServiceClient).createUser(anyString(), any(), any(), any());

        //when
        // Вызываем тестируемый метод регистрации пользователя
        TokenResponse response = authService.register(registerRequest);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertEquals("access-token", response.getAccessToken()); // Проверка: что access токен совпадает
        assertEquals("refresh-token", response.getRefreshToken()); // Проверка: что refresh токен совпадает
        assertEquals(900000L, response.getExpiresIn()); // Проверка: что время жизни токена совпадает
        verify(userRepository).existsByLogin("newuser"); // Проверка: что проверка существования логина была вызвана
        verify(passwordEncoder).encode("password123"); // Проверка: что пароль был захеширован
        verify(userRepository).save(any(User.class)); // Проверка: что пользователь был сохранён в БД
        verify(jwtTokenProvider).generateAccessToken("newuser", Role.ROLE_USER); // Проверка: что access токен был сгенерирован
        verify(jwtTokenProvider).generateRefreshToken("newuser", Role.ROLE_USER); // Проверка: что refresh токен был сгенерирован
    }

    @Test
    @DisplayName("register - пользователь уже существует")
    void register_ShouldThrowException_WhenUserAlreadyExists() {
        // given & when
        // Когда кто-то вызовет userRepository.existsByLogin("newuser"), верни true
        // Это имитирует ситуацию, когда пользователь с таким логином уже существует в базе данных
        when(userRepository.existsByLogin("newuser")).thenReturn(true);

        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.register(registerRequest));

        // then
        assertEquals("Login already exists", exception.getMessage()); // Проверка: что сообщение исключения совпадает
        verify(userRepository).existsByLogin("newuser"); // Проверка: что проверка существования логина была вызвана
        verify(userRepository, never()).save(any(User.class)); // Проверка: что сохранение НЕ было вызвано
        // (если пользователь уже существует, сохранять его не нужно)
    }

    @Test
    @DisplayName("register - попытка регистрации с ролью ADMIN")
    void register_ShouldThrowException_WhenTryingToRegisterAsAdmin() {
        // given
        // Устанавливаем роль ADMIN в запросе регистрации
        // Это проверяет, что через публичный endpoint нельзя создать администратора
        registerRequest.setRole("ADMIN");
        // Когда кто-то вызовет userRepository.existsByLogin("newuser"), верни false
        when(userRepository.existsByLogin("newuser")).thenReturn(false);

        //when & then
        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.register(registerRequest));

        assertEquals("Cannot register with ADMIN role", exception.getMessage()); // Проверка: что сообщение исключения совпадает
        verify(userRepository).existsByLogin("newuser"); // Проверка: что проверка существования логина была вызвана
        verify(userRepository, never()).save(any(User.class)); // Проверка: что сохранение НЕ было вызвано
        // (нельзя регистрировать администратора через публичный endpoint)
    }

    @Test
    @DisplayName("register - регистрация с ролью ROLE_USER")
    void register_ShouldAcceptRoleUser_WhenRoleIsRoleUser() {
        // given
        // Устанавливаем роль ROLE_USER в запросе регистрации
        registerRequest.setRole("ROLE_USER");
        // Когда кто-то вызовет userRepository.existsByLogin("newuser"), верни false
        when(userRepository.existsByLogin("newuser")).thenReturn(false);
        // Когда кто-то вызовет passwordEncoder.encode("password123"), верни хеш пароля
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encodedPassword");
        // Мокируем вызов userServiceClient.createUser (не выбрасывает исключение)
        doNothing().when(userServiceClient).createUser(anyString(), any(), any(), any());

        //when
        // Вызываем тестируемый метод регистрации пользователя
        authService.register(registerRequest);

        // then
        // Проверка: что пользователь был сохранён с ролью ROLE_USER
        // argThat позволяет проверить, что сохранённый объект соответствует условию
        verify(userRepository).save(argThat(user -> user.getRole() == Role.ROLE_USER));
    }

    @Test
    @DisplayName("register - регистрация с невалидной ролью")
    void register_ShouldThrowException_WhenRoleIsInvalid() {
        // given
        // Устанавливаем невалидную роль в запросе регистрации
        // Это проверяет валидацию роли при регистрации
        registerRequest.setRole("INVALID_ROLE");
        // Когда кто-то вызовет userRepository.existsByLogin("newuser"), верни false
        when(userRepository.existsByLogin("newuser")).thenReturn(false);

        //when & then
        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.register(registerRequest));

        assertTrue(exception.getMessage().contains("Invalid role")); // Проверка: что сообщение содержит информацию о невалидной роли
        verify(userRepository).existsByLogin("newuser"); // Проверка: что проверка существования логина была вызвана
        verify(userRepository, never()).save(any(User.class)); // Проверка: что сохранение НЕ было вызвано
        // (нельзя сохранить пользователя с невалидной ролью)
    }

    @Test
    @DisplayName("refreshToken - успешное обновление токена")
    void refreshToken_ShouldReturnNewTokens_WhenRefreshTokenIsValid() {
        // given
        String refreshToken = "valid-refresh-token";
        // Когда кто-то вызовет jwtTokenProvider.validateToken(refreshToken), верни true
        // Это имитирует успешную валидацию refresh токена
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        // Когда кто-то вызовет jwtTokenProvider.getUsernameFromToken(refreshToken), верни "testuser"
        // Это извлекает имя пользователя из токена
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn("testuser");
        // Когда кто-то вызовет jwtTokenProvider.getRoleFromToken(refreshToken), верни "ROLE_USER"
        // Это извлекает роль пользователя из токена
        when(jwtTokenProvider.getRoleFromToken(refreshToken)).thenReturn("ROLE_USER");
        // Когда кто-то вызовет userRepository.findByLogin("testuser"), верни Optional с testUser
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(testUser));
        // Когда кто-то вызовет jwtTokenProvider.generateAccessToken(...), верни новый access токен
        when(jwtTokenProvider.generateAccessToken("testuser", Role.ROLE_USER)).thenReturn("new-access-token");
        // Когда кто-то вызовет jwtTokenProvider.generateRefreshToken(...), верни новый refresh токен
        when(jwtTokenProvider.generateRefreshToken("testuser", Role.ROLE_USER)).thenReturn("new-refresh-token");
        // Когда кто-то вызовет jwtTokenProvider.getJwtExpiration(), верни время жизни токена
        when(jwtTokenProvider.getJwtExpiration()).thenReturn(900000L);

        //when
        // Вызываем тестируемый метод обновления токена
        TokenResponse response = authService.refreshToken(refreshToken);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertEquals("new-access-token", response.getAccessToken()); // Проверка: что новый access токен совпадает
        assertEquals("new-refresh-token", response.getRefreshToken()); // Проверка: что новый refresh токен совпадает
        verify(jwtTokenProvider).validateToken(refreshToken); // Проверка: что токен был валидирован
        verify(jwtTokenProvider).getUsernameFromToken(refreshToken); // Проверка: что имя пользователя было извлечено
        verify(jwtTokenProvider).getRoleFromToken(refreshToken); // Проверка: что роль была извлечена
        verify(userRepository).findByLogin("testuser"); // Проверка: что пользователь был найден в БД
    }

    @Test
    @DisplayName("refreshToken - невалидный refresh токен")
    void refreshToken_ShouldThrowException_WhenRefreshTokenIsInvalid() {
        // given & when
        String refreshToken = "invalid-refresh-token";
        // Когда кто-то вызовет jwtTokenProvider.validateToken(refreshToken), верни false
        // Это имитирует ситуацию, когда refresh токен невалиден (истёк, повреждён и т.д.)
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.refreshToken(refreshToken));

        // then
        assertEquals("Invalid refresh token", exception.getMessage()); // Проверка: что сообщение исключения совпадает
        verify(jwtTokenProvider).validateToken(refreshToken); // Проверка: что токен был валидирован
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString()); // Проверка: что извлечение имени НЕ было вызвано
        // (если токен невалиден, извлекать из него данные не нужно)
    }

    @Test
    @DisplayName("refreshToken - пользователь не найден")
    void refreshToken_ShouldThrowException_WhenUserNotFound() {
        // given & when
        String refreshToken = "valid-refresh-token";
        // Когда кто-то вызовет jwtTokenProvider.validateToken(refreshToken), верни true
        // Токен валиден, но пользователя в БД нет
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        // Когда кто-то вызовет jwtTokenProvider.getUsernameFromToken(refreshToken), верни "nonexistent"
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn("nonexistent");
        // Когда кто-то вызовет jwtTokenProvider.getRoleFromToken(refreshToken), верни "ROLE_USER"
        when(jwtTokenProvider.getRoleFromToken(refreshToken)).thenReturn("ROLE_USER");
        // Когда кто-то вызовет userRepository.findByLogin("nonexistent"), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким логином не существует в базе данных
        when(userRepository.findByLogin("nonexistent")).thenReturn(Optional.empty());

        // Вызываем тестируемый метод и ожидаем выброс исключения
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.refreshToken(refreshToken));

        // then
        assertEquals("User not found", exception.getMessage()); // Проверка: что сообщение исключения совпадает
        verify(userRepository).findByLogin("nonexistent"); // Проверка: что поиск пользователя был выполнен
    }

    @Test
    @DisplayName("refreshToken - несоответствие роли")
    void refreshToken_ShouldThrowException_WhenRoleMismatch() {
        // given
        String refreshToken = "valid-refresh-token";
        // Создаём пользователя с ролью ADMIN для проверки несоответствия роли
        User adminUser = new User();
        adminUser.setLogin("testuser");
        adminUser.setRole(Role.ROLE_ADMIN); // В БД пользователь имеет роль ADMIN

        // Когда кто-то вызовет jwtTokenProvider.validateToken(refreshToken), верни true
        when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
        // Когда кто-то вызовет jwtTokenProvider.getUsernameFromToken(refreshToken), верни "testuser"
        when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn("testuser");
        // Когда кто-то вызовет jwtTokenProvider.getRoleFromToken(refreshToken), верни "ROLE_USER"
        // Но в БД пользователь имеет роль ROLE_ADMIN — это несоответствие
        when(jwtTokenProvider.getRoleFromToken(refreshToken)).thenReturn("ROLE_USER");
        // Когда кто-то вызовет userRepository.findByLogin("testuser"), верни Optional с adminUser
        when(userRepository.findByLogin("testuser")).thenReturn(Optional.of(adminUser));

        //when & then
        // Вызываем тестируемый метод и ожидаем выброс исключения
        // Это проверяет дополнительную проверку безопасности: роль в токене должна совпадать с ролью в БД
        AuthenticationException exception = assertThrows(AuthenticationException.class,
                () -> authService.refreshToken(refreshToken));

        assertEquals("Role mismatch: token role does not match user role", exception.getMessage()); // Проверка: что сообщение исключения совпадает
    }

    @Test
    @DisplayName("validateToken - валидный токен")
    void validateToken_ShouldReturnValidResponse_WhenTokenIsValid() {
        // given
        String token = "valid-token";
        // Когда кто-то вызовет jwtTokenProvider.validateToken(token), верни true
        // Это имитирует успешную валидацию токена
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        // Когда кто-то вызовет jwtTokenProvider.getUsernameFromToken(token), верни "testuser"
        when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("testuser");
        // Когда кто-то вызовет jwtTokenProvider.getRoleFromToken(token), верни "ROLE_USER"
        when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("ROLE_USER");

        //when
        // Вызываем тестируемый метод валидации токена
        TokenValidationResponse response = authService.validateToken(token);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertTrue(response.isValid()); // Проверка: что токен валиден
        assertEquals("testuser", response.getUsername()); // Проверка: что имя пользователя совпадает
        assertEquals("ROLE_USER", response.getRole()); // Проверка: что роль совпадает
        verify(jwtTokenProvider).validateToken(token); // Проверка: что токен был валидирован
        verify(jwtTokenProvider).getUsernameFromToken(token); // Проверка: что имя пользователя было извлечено
        verify(jwtTokenProvider).getRoleFromToken(token); // Проверка: что роль была извлечена
    }

    @Test
    @DisplayName("validateToken - невалидный токен")
    void validateToken_ShouldReturnInvalidResponse_WhenTokenIsInvalid() {
        // given
        String token = "invalid-token";
        // Когда кто-то вызовет jwtTokenProvider.validateToken(token), верни false
        // Это имитирует ситуацию, когда токен невалиден (истёк, повреждён и т.д.)
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        //when
        // Вызываем тестируемый метод валидации токена
        TokenValidationResponse response = authService.validateToken(token);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertFalse(response.isValid()); // Проверка: что токен невалиден
        assertNull(response.getUsername()); // Проверка: что имя пользователя null (не извлекается для невалидного токена)
        assertNull(response.getRole()); // Проверка: что роль null (не извлекается для невалидного токена)
        verify(jwtTokenProvider).validateToken(token); // Проверка: что токен был валидирован
        verify(jwtTokenProvider, never()).getUsernameFromToken(anyString()); // Проверка: что извлечение имени НЕ было вызвано
        // (если токен невалиден, извлекать из него данные не нужно)
    }

    @Test
    @DisplayName("validateToken - исключение при валидации")
    void validateToken_ShouldReturnInvalidResponse_WhenExceptionOccurs() {
        // given
        String token = "token-that-throws-exception";
        // Когда кто-то вызовет jwtTokenProvider.validateToken(token), выбрось исключение
        // Это имитирует ситуацию, когда при валидации токена происходит ошибка (например, некорректный формат)
        when(jwtTokenProvider.validateToken(token)).thenThrow(new RuntimeException("Token parsing error"));

        //when
        // Вызываем тестируемый метод валидации токена
        // Метод должен обработать исключение и вернуть невалидный ответ (не пробросить исключение дальше)
        TokenValidationResponse response = authService.validateToken(token);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertFalse(response.isValid()); // Проверка: что токен невалиден (из-за исключения)
        assertNull(response.getUsername()); // Проверка: что имя пользователя null
        assertNull(response.getRole()); // Проверка: что роль null
        // Метод должен безопасно обрабатывать исключения и возвращать невалидный ответ вместо проброса исключения
    }
}

