package com.innowise.authenticationservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RefreshTokenRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.dto.UpdateUserProfileRequest;
import com.innowise.authenticationservice.service.AuthService;

@WebMvcTest(AuthController.class) //То есть мы тестируем только контроллер AuthController, а не всю систему
//поднимает только web-слой Spring Boot:
//контроллеры (@RestController, @Controller);
//@ControllerAdvice (например, глобальные обработчики ошибок);
//JSON сериализацию / десериализацию (Jackson);
//маршрутизацию (DispatcherServlet, MockMvc);
//но не поднимает сервисы, репозитории, базы данных — всё остальное мокается.
@AutoConfigureMockMvc(addFilters = false) //Создает MockMvc (имитацию HTTP-запросов), но не включает фильтры безопасности (Spring Security, CORS и т.п.)
@TestPropertySource(properties = "internal.api.key=test-key")
@SuppressWarnings({"null", "removal"})
class AuthControllerTest {

    //@SuppressWarnings("unchecked")   // подавляет предупреждения о "сырых" generic-типах
    //@SuppressWarnings("deprecation") // подавляет использование устаревших API
    //@SuppressWarnings("rawtypes")    // подавляет "Raw use of parameterized class"
    //@SuppressWarnings("unused")      // подавляет "переменная не используется"
    //@SuppressWarnings("null")         // подавляет NPE
    @Autowired
    private MockMvc mockMvc; //тестовый инструмент, имитирующий HTTP-запросы (GET/POST и т.д.), без запуска настоящего сервера

    @Autowired
    private ObjectMapper objectMapper; //сериализатор Jackson для преобразования объектов Java → JSON

    @MockBean
    private AuthService authService; //замоканный (mock) сервис, внедряемый в AuthController вместо настоящего бина (без базы и Keycloak)

    //@Nested группирует тесты по эндпоинту:
    @Nested
    class LoginEndpoint {
        @Test
        @DisplayName("POST /auth/login returns tokens")
        void loginReturnsTokens() throws Exception {
            TokenResponse response = new TokenResponse("access", "refresh", 3600L);
            //Задаём поведение мок-сервиса. При вызове authService.login() он вернёт фиктивный TokenResponse
            when(authService.login(any(LoginRequest.class))).thenReturn(response);
            //Создаём JSON-запрос который сериализуется в:  "username": "user", "password": "pass"
            LoginRequest request = new LoginRequest("user", "pass");

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()) //HTTP статус 200 OK
                    .andExpect(jsonPath("$.accessToken").value("access"))//проверка филдов
                    .andExpect(jsonPath("$.refreshToken").value("refresh"))//проверка данных в ответе JSON
                    .andExpect(jsonPath("$.expiresIn").value(3600L))
                    .andExpect(jsonPath("$.type").value("Bearer"));

            verify(authService).login(any(LoginRequest.class));// метод вызван ровно один раз
        }
    }

    @Nested
    class RegisterEndpoint {
        @Test
        @DisplayName("POST /auth/register returns OK")
        void registerReturnsOk() throws Exception { //Проверка, что этот запрос вызывает сервис
            RegisterRequest request = new RegisterRequest("user", "pass", "John", "Doe", "ROLE_USER");
            doNothing().when(authService).register(any(RegisterRequest.class));

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk()); //на статус HTTP 200

            verify(authService).register(any(RegisterRequest.class));
        }
    }

    @Nested
    class RefreshEndpoint {
        @Test
        @DisplayName("POST /auth/refresh returns new tokens")
        void refreshReturnsTokens() throws Exception {
            TokenResponse response = new TokenResponse("new-access", "new-refresh", 7200L);
            when(authService.refreshToken("refresh-token")).thenReturn(response);

            RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");

            mockMvc.perform(post("/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-access"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                    .andExpect(jsonPath("$.expiresIn").value(7200L));

            verify(authService).refreshToken("refresh-token");
        }
    }

    @Nested
    class CreateTokenEndpoint {
        @Test
        @DisplayName("POST /auth/create-token delegates to login")
        void createTokenDelegatesToLogin() throws Exception {
            TokenResponse response = new TokenResponse("access", "refresh", 3600L);
            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            LoginRequest request = new LoginRequest("user", "pass");

            mockMvc.perform(post("/auth/create-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("access"));

            verify(authService).login(any(LoginRequest.class));
        }
    }

    @Nested
    class ValidateEndpoint {
        @Test
        @DisplayName("POST /auth/validate returns validation result")
        void validateReturnsResult() throws Exception {
            TokenValidationResponse response = new TokenValidationResponse(true, "user", "ROLE_USER");
            when(authService.validateToken("token")).thenReturn(response);

            TokenValidationRequest request = new TokenValidationRequest("token");

            mockMvc.perform(post("/auth/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.username").value("user"))
                    .andExpect(jsonPath("$.role").value("ROLE_USER"));

            verify(authService).validateToken("token");
        }
    }

    @Nested
    class UpdateProfileEndpoint {
        @Test
        @DisplayName("PUT /auth/users/profile with valid key updates profile")
        void updateProfileWithValidKey() throws Exception {
            UpdateUserProfileRequest request = new UpdateUserProfileRequest("old@example.com", "new@example.com", "First", "Last");
            doNothing().when(authService).updateUserProfile(any(UpdateUserProfileRequest.class));

            mockMvc.perform(put("/auth/users/profile")
                            .header("X-Internal-Api-Key", "test-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            verify(authService).updateUserProfile(any(UpdateUserProfileRequest.class));
        }

        @Test
        @DisplayName("PUT /auth/users/profile with invalid key is unauthorized")
        void updateProfileWithInvalidKey() throws Exception {
            UpdateUserProfileRequest request = new UpdateUserProfileRequest("old@example.com", "new@example.com", "First", "Last");

            mockMvc.perform(put("/auth/users/profile")
                            .header("X-Internal-Api-Key", "wrong-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid internal API key"));
        }
    }
}

