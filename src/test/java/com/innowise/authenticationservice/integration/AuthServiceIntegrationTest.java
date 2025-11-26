package com.innowise.authenticationservice.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.innowise.authenticationservice.client.UserServiceClient;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.exception.AuthenticationException;
import com.innowise.authenticationservice.model.Role;
import com.innowise.authenticationservice.repository.UserRepository;
import com.innowise.authenticationservice.service.AuthService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")//вкл профиль тест - подкл application-test.properties
@Testcontainers//библиотека автоматически управляет жизненным циклом контейнеров (если используются аннотации @Container)
@SuppressWarnings("resource") //IDE/компилятор предупреждает, что PostgreSQLContainer как AutoCloseable потенциально не закрыт.
// Поскольку тестконтейнер статический и управляется Testcontainers, подавляем это предупреждение
class AuthServiceIntegrationTest {

    @Container
//Создаётся статический контейнер PostgreSQL (образ postgres:16) — один контейнер на весь класс теста(поднимается до один раз для запуска тестов и выключается после)
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("auth_db_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    //Динамически добавляет свойства в Spring Environment во время старта тестов. Здесь подставляем URL/логин/пароль JDBC из контейнера в Spring
    static void configureProperties(DynamicPropertyRegistry registry) {//тест использует реально поднятую PostgreSQL и не зависит от локальной БД
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("keycloak.enabled", () -> "false");//отключает интеграцию с Keycloak для тестового прогона (чтобы не требовать живого Keycloak)
    }

    @Autowired //Тестируем реальные
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private UserServiceClient userServiceClient; // Мокируем user-service клиент, чтобы не вызывать реальный сервис

    //для теста данные
    private static final String LOGIN = "integration@example.com";
    private static final String PASSWORD = "secret";

    @BeforeEach
    void setUp() {// Очистка таблицы перед каждым тестом
        userRepository.deleteAll();
        // Мокируем вызов userServiceClient.createUser(), чтобы он не выбрасывал исключение
        doNothing().when(userServiceClient).createUser(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        long count = userRepository.count();
        assert count == 0 : "After test, DB should be empty but has " + count + " rows";
    }

    @Test
    @DisplayName("register persists user in database")
    void registerPersistsUser() {//проверяем что регистрация реально сохраняет сущность пользователя в БД и интеграция AuthService -> repository -> DB работает
        RegisterRequest request = new RegisterRequest();
        request.setLogin(LOGIN);
        request.setPassword(PASSWORD);
        request.setRole(Role.ROLE_USER.name());

        authService.register(request);

        assertThat(userRepository.existsByLogin(LOGIN)).isTrue();
    }

    @Test
    @DisplayName("login returns tokens for persisted user")
    void loginReturnsTokens() {// login возвращает JWT/токены, проверка логики генерации и выдачи токенов, проверка самого токена
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setLogin(LOGIN);
        registerRequest.setPassword(PASSWORD);
        registerRequest.setRole(Role.ROLE_USER.name());
        
        authService.register(registerRequest);

        TokenResponse response = authService.login(new LoginRequest(LOGIN, PASSWORD));

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isNotNull();
        assertThat(response.getType()).isEqualToIgnoringCase("bearer");
    }

    @Test
    @DisplayName("login throws for unknown user")
    void loginUnknownUser() { //проверить вариант пользователь не найден и корректную обработку ошибок

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.login(new LoginRequest(LOGIN, PASSWORD)));

        assertThat(ex.getMessage()).isEqualTo("Invalid login or password");
    }
}

