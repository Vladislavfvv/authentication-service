package com.innowise.authenticationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RefreshTokenRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.exception.AuthenticationException;
import com.innowise.authenticationservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты для AuthController.
 * Проверяет работу всех endpoints контроллера аутентификации.
 */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private TokenResponse tokenResponse;
    private TokenValidationResponse validationResponse;

    @BeforeEach
    void setUp() {
        tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("test-access-token");
        tokenResponse.setRefreshToken("test-refresh-token");
        tokenResponse.setType("Bearer");
        tokenResponse.setExpiresIn(900000L);

        validationResponse = new TokenValidationResponse();
        validationResponse.setValid(true);
        validationResponse.setUsername("testuser");
        validationResponse.setRole("ROLE_USER");
    }

    @Test
    @DisplayName("POST /auth/v1/login - успешный вход")
    void login_ShouldReturnTokens_WhenCredentialsAreValid() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLogin("testuser");
        loginRequest.setPassword("password123");

        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("test-refresh-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900000L));
    }

    @Test
    @DisplayName("POST /auth/v1/login - неверные учетные данные")
    void login_ShouldReturnUnauthorized_WhenCredentialsAreInvalid() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLogin("testuser");
        loginRequest.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new AuthenticationException("Invalid login or password"));

        // when & then
        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/v1/login - валидация: пустой login")
    void login_ShouldReturnBadRequest_WhenLoginIsBlank() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLogin(""); // пустой login
        loginRequest.setPassword("password123");

        // when & then
        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/v1/login - валидация: пустой password")
    void login_ShouldReturnBadRequest_WhenPasswordIsBlank() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLogin("testuser");
        loginRequest.setPassword(""); // пустой password

        // when & then
        mockMvc.perform(post("/auth/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/v1/register - успешная регистрация")
    void register_ShouldReturnCreated_WhenRegistrationIsSuccessful() throws Exception {
        // given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setLogin("newuser");
        registerRequest.setPassword("password123");
        registerRequest.setRole("USER");

        // when & then
        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value("User registered successfully. Please login to get tokens."));
    }

    @Test
    @DisplayName("POST /auth/v1/register - пользователь уже существует")
    void register_ShouldReturnUnauthorized_WhenUserAlreadyExists() throws Exception {
        // given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setLogin("existinguser");
        registerRequest.setPassword("password123");
        registerRequest.setRole("USER");

        doThrow(new AuthenticationException("Login already exists"))
                .when(authService).register(any(RegisterRequest.class));

        // when & then
        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/v1/register - попытка регистрации с ролью ADMIN")
    void register_ShouldReturnUnauthorized_WhenTryingToRegisterAsAdmin() throws Exception {
        // given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setLogin("adminuser");
        registerRequest.setPassword("password123");
        registerRequest.setRole("ADMIN");

        doThrow(new AuthenticationException("Cannot register with ADMIN role"))
                .when(authService).register(any(RegisterRequest.class));

        // when & then
        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/v1/register - валидация: пустой login")
    void register_ShouldReturnBadRequest_WhenLoginIsBlank() throws Exception {
        // given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setLogin(""); // пустой login
        registerRequest.setPassword("password123");
        registerRequest.setRole("USER");

        // when & then
        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/v1/register - валидация: пустой password")
    void register_ShouldReturnBadRequest_WhenPasswordIsBlank() throws Exception {
        // given
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setLogin("newuser");
        registerRequest.setPassword(""); // пустой password
        registerRequest.setRole("USER");

        // when & then
        mockMvc.perform(post("/auth/v1/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/v1/refresh - успешное обновление токена")
    void refreshToken_ShouldReturnNewTokens_WhenRefreshTokenIsValid() throws Exception {
        // given
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken("valid-refresh-token");

        when(authService.refreshToken(anyString())).thenReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("test-refresh-token"));
    }

    @Test
    @DisplayName("POST /auth/v1/refresh - невалидный refresh токен")
    void refreshToken_ShouldReturnUnauthorized_WhenRefreshTokenIsInvalid() throws Exception {
        // given
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken("invalid-refresh-token");

        when(authService.refreshToken(anyString()))
                .thenThrow(new AuthenticationException("Invalid refresh token"));

        // when & then
        mockMvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/v1/refresh - валидация: пустой refresh token")
    void refreshToken_ShouldReturnBadRequest_WhenRefreshTokenIsBlank() throws Exception {
        // given
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(""); // пустой refresh token

        // when & then
        mockMvc.perform(post("/auth/v1/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/v1/create-token - успешное создание токена")
    void createToken_ShouldReturnTokens_WhenCredentialsAreValid() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLogin("testuser");
        loginRequest.setPassword("password123");

        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/v1/create-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("test-refresh-token"));
    }

    @Test
    @DisplayName("POST /auth/v1/validate - успешная валидация токена")
    void validateToken_ShouldReturnValidationResponse_WhenTokenIsValid() throws Exception {
        // given
        TokenValidationRequest validationRequest = new TokenValidationRequest();
        validationRequest.setToken("valid-token");

        when(authService.validateToken(anyString())).thenReturn(validationResponse);

        // when & then
        mockMvc.perform(post("/auth/v1/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /auth/v1/validate - невалидный токен")
    void validateToken_ShouldReturnInvalidResponse_WhenTokenIsInvalid() throws Exception {
        // given
        TokenValidationRequest validationRequest = new TokenValidationRequest();
        validationRequest.setToken("invalid-token");

        TokenValidationResponse invalidResponse = new TokenValidationResponse();
        invalidResponse.setValid(false);
        invalidResponse.setUsername(null);
        invalidResponse.setRole(null);

        when(authService.validateToken(anyString())).thenReturn(invalidResponse);

        // when & then
        mockMvc.perform(post("/auth/v1/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.username").isEmpty())
                .andExpect(jsonPath("$.role").isEmpty());
    }

    @Test
    @DisplayName("POST /auth/v1/validate - валидация: пустой token")
    void validateToken_ShouldReturnBadRequest_WhenTokenIsBlank() throws Exception {
        // given
        TokenValidationRequest validationRequest = new TokenValidationRequest();
        validationRequest.setToken(""); // пустой token

        // when & then
        mockMvc.perform(post("/auth/v1/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validationRequest)))
                .andExpect(status().isBadRequest());
    }
}

