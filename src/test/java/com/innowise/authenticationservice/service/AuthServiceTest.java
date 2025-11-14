package com.innowise.authenticationservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

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
import com.innowise.authenticationservice.client.UserServiceClient;

@ExtendWith(MockitoExtension.class)//автоматически инициализирует поля, помеченные @Mock
class AuthServiceTest {
    //@SuppressWarnings("unchecked")   // подавляет предупреждения о "сырых" generic-типах
    //@SuppressWarnings("deprecation") // подавляет использование устаревших API
    //@SuppressWarnings("rawtypes")    // подавляет "Raw use of parameterized class"
    //@SuppressWarnings("unused")      // подавляет "переменная не используется"
    //@SuppressWarnings("null")         // подавляет NPE

    //any(); - // любое значение этого типа
    //anyString();      // любое значение типа String
    //anyInt();         // любое значение типа int
    //any(Role.class);  // любой объект типа Role
    //eq("Roma");       // точно значение "Roma"


    private static final String LOGIN = "user@example.com";
    private static final String PASSWORD = "secret";
    private static final String PASSWORD_HASH = "hashed";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private KeycloakService keycloakService;

    @Mock
    private UserServiceClient userServiceClient;

    private AuthService authService;

    @BeforeEach//Каждый тест получает свежий экземпляр AuthService со статическими моками
    void setUp() {
        authService = new AuthService( //authService создаётся вручную в @BeforeEach с этими моками (т.е. тестируем реальную имплементацию, но с "поддельными" зависимостями)
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                Optional.of(keycloakService), //сервис может работать с Keycloak (в некоторых конфигурациях Keycloak может отсутствовать — тогда туда передаем empty)
                Optional.of(userServiceClient) //клиент для синхронизации с user-service
        );
    }

    private User sampleUser() { //создание тестового пользователя (в т.ч. с уже захешированным паролем)

        return new User(LOGIN,
                PASSWORD_HASH,
                Role.ROLE_USER,
                "firstname",
                "lastname");
    }

    @Nested//оказывается тесты прикольно распределяются по вложенным классам - как подпапки для разделения логики
    class LoginTests {
        @Test
        @DisplayName("login returns tokens when credentials are valid")
        void loginSuccess() {
            // given
            LoginRequest request = new LoginRequest(LOGIN, PASSWORD);
            User user = sampleUser();

            when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(LOGIN, Role.ROLE_USER)).thenReturn("access");
            when(jwtTokenProvider.generateRefreshToken(LOGIN, Role.ROLE_USER)).thenReturn("refresh");
            when(jwtTokenProvider.getJwtExpiration()).thenReturn(3600L);

            //when вызов метода
            TokenResponse response = authService.login(request);

            //then сравнение
            assertNotNull(response);
            assertEquals("access", response.getAccessToken());
            assertEquals("refresh", response.getRefreshToken());
            assertEquals(3600L, response.getExpiresIn());
        }

        @Test
        @DisplayName("login throws when user not found")
        void loginUserNotFound() {
            //given+when вызов метода
            when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.empty());

            //then сравнение
            AuthenticationException ex = assertThrows(
                    AuthenticationException.class,
                    () -> authService.login(new LoginRequest(LOGIN, PASSWORD))
            );

            assertEquals("Invalid login or password", ex.getMessage());
        }

        @Test
        @DisplayName("login throws when password mismatched")
        void loginInvalidPassword() {
            // given
            User user = sampleUser();

            //when вызов метода
            when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

            //then
            AuthenticationException ex = assertThrows(
                    AuthenticationException.class,
                    () -> authService.login(new LoginRequest(LOGIN, PASSWORD))
            );

            assertEquals("Invalid login or password", ex.getMessage());
        }
    }

    @Nested
    class RegisterTests {
        @Test
        @DisplayName("register persists user and syncs with Keycloak and user-service")
        @SuppressWarnings("null")//подавление предупреждения компилятора, связанного с потенциальными null-значениями
        void registerSuccess() {//вызывает userRepository.save() с корректной сущностью и синхронизирует пользователя с Keycloak и user-service
            RegisterRequest request = new RegisterRequest(LOGIN, PASSWORD, "firstname", "lastname", "ROLE_USER");

            when(userRepository.existsByLogin(LOGIN)).thenReturn(false);//нет конфликта логина
            when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);//поведение кодировщика
            when(userRepository.save(any(User.class))).thenAnswer((Answer<User>) invocation -> {//перехватывает аргумент (сохраняемого User) и проверяет его поля (assert внутри thenAnswer).
                //Mockito и Answer<User> — обобщённый тип? Компилятор не может гарантировать, что invocation.getArgument(0, User.class) не вернёт null.
                //Поэтому он предупреждает: "возможен null при разыменовании"/ ручную проверяем assertNotNull(savedUser); и уверен, что null не будет
                //thenAnswer используется вместо простого thenReturn, чтобы протестировать, какую сущность сервис пытается сохранить (а не только что вызов был)
                User savedUser = invocation.getArgument(0, User.class);
                assertNotNull(savedUser);
                assertEquals(LOGIN, savedUser.getLogin());
                assertEquals("firstname", savedUser.getFirstName());
                assertEquals("lastname", savedUser.getLastName());
                assertEquals(Role.ROLE_USER, savedUser.getRole());
                return savedUser;
            });
            // Мокируем вызов userServiceClient.createUser() чтобы он не выбрасывал исключение
            doNothing().when(userServiceClient).createUser(any(String.class), any(String.class), any(String.class));

            authService.register(request);

            verify(keycloakService).createUser(
                    eq(LOGIN),
                    eq(PASSWORD),
                    eq(Role.ROLE_USER),
                    eq("firstname"),
                    eq("lastname")
            );
            // Проверяем, что userServiceClient.createUser() был вызван
            verify(userServiceClient).createUser(
                    eq(LOGIN),
                    eq("firstname"),
                    eq("lastname")
            );
        }

        @Test
        @DisplayName("register throws when login already exists")
        void registerDuplicateLogin() {
            when(userRepository.existsByLogin(LOGIN)).thenReturn(true);

            RegisterRequest request = new RegisterRequest(LOGIN, PASSWORD, "firstname", "lastname", "ROLE_USER");

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> authService.register(request));
            assertEquals("Login already exists", ex.getMessage());
            //проверка, что мок вызван с ожидаемыми аргументами
            verify(keycloakService, never()).createUser(any(), any(), any(), any(), any());//способ проверить, что определённый метод был вызван (или не был вызван) на мок-объекте (mock) в ходе теста
            //аналог - verify(keycloakService, times(0)).createUser(any(), any(), any(), any(), any())
            //keycloakService — это мок (замокированный объект)
            //never() - верификационный режим, который означает "ожидаем 0 вызовов". Альтернатива — times(0)
            //any() - матчер (matcher) — он говорит Mockito: "мне всё равно, что передавалось в аргумент, подойдёт любое значение этого типа"
            // ИТОГО: в данном случаеп проверка метода createUser() - убеждаемся, что он не вызывался неважно какие аргументы были переданы
            verify(userServiceClient, never()).createUser(any(), any(), any());//проверяем, что userServiceClient.createUser() также не был вызван
        }

        @Test
        @DisplayName("register throws when role string is invalid")
        void registerInvalidRole() {
            RegisterRequest request = new RegisterRequest(LOGIN, PASSWORD, "firstname", "lastname", "invalid-role");

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> authService.register(request));
            assertEquals("Invalid role: invalid-role", ex.getMessage());
        }

        @Test
        @DisplayName("register throws when trying to create admin")//админ у нас один, больше не добавляем
        void registerAdminForbidden() {
            RegisterRequest request = new RegisterRequest(LOGIN, PASSWORD, "firstname", "lastname", "ROLE_ADMIN");

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> authService.register(request));
            assertEquals("Cannot register with ADMIN role", ex.getMessage());
        }
    }

    @Nested
    class RefreshTokenTests {
        @Test
        @DisplayName("refreshToken returns new tokens when refresh token valid")
        void refreshTokenSuccess() {
            String refreshToken = "refresh";
            User user = sampleUser();

            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(LOGIN);
            when(jwtTokenProvider.getRoleFromToken(refreshToken)).thenReturn(Role.ROLE_USER.name());
            when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(LOGIN, Role.ROLE_USER)).thenReturn("new-access");
            when(jwtTokenProvider.generateRefreshToken(LOGIN, Role.ROLE_USER)).thenReturn("new-refresh");
            when(jwtTokenProvider.getJwtExpiration()).thenReturn(7200L); //TokenResponse содержит ожидаемые новые токены и expiresIn = 7200L

            TokenResponse response = authService.refreshToken(refreshToken);

            assertEquals("new-access", response.getAccessToken());
            assertEquals("new-refresh", response.getRefreshToken());
            assertEquals(7200L, response.getExpiresIn());
        }

        @Test
        @DisplayName("refreshToken throws when token is invalid")
        void refreshTokenInvalid() {
            when(jwtTokenProvider.validateToken("bad")).thenReturn(false);

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> authService.refreshToken("bad"));
            assertEquals("Invalid refresh token", ex.getMessage());
        }

        @Test
        @DisplayName("refreshToken throws when user not found")
        void refreshTokenUserMissing() {//токен валиден, но в БД пользователь не найден
            String refreshToken = "refresh";

            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(refreshToken)).thenReturn(LOGIN);
            when(jwtTokenProvider.getRoleFromToken(refreshToken)).thenReturn(Role.ROLE_USER.name());
            when(userRepository.findByLogin(LOGIN)).thenReturn(Optional.empty());

            AuthenticationException ex = assertThrows(AuthenticationException.class, () -> authService.refreshToken(refreshToken));
            assertEquals("User not found", ex.getMessage());
        }
    }

    @Nested
    class ValidateTokenTests {
        @Test
        @DisplayName("validateToken returns valid response when JWT valid")
        void validateTokenSuccess() {
            String token = "token";

            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn(LOGIN);
            when(jwtTokenProvider.getRoleFromToken(token)).thenReturn(Role.ROLE_USER.name());

            TokenValidationResponse response = authService.validateToken(token);

            assertEquals(true, response.isValid());
            assertEquals(LOGIN, response.getUsername());
            assertEquals(Role.ROLE_USER.name(), response.getRole());
        }

        @Test
        @DisplayName("validateToken returns invalid response when JWT invalid")
        void validateTokenInvalid() {
            String token = "token";

            when(jwtTokenProvider.validateToken(token)).thenReturn(false);

            TokenValidationResponse response = authService.validateToken(token);

            assertEquals(false, response.isValid());
            assertEquals(null, response.getUsername());
            assertEquals(null, response.getRole());
        }

        @Test
        @DisplayName("validateToken returns invalid response when exception thrown")
        void validateTokenException() {
            String token = "token";

            when(jwtTokenProvider.validateToken(token)).thenThrow(new RuntimeException("decode error"));

            TokenValidationResponse response = authService.validateToken(token);

            assertEquals(false, response.isValid());
            assertEquals(null, response.getUsername());
            assertEquals(null, response.getRole());
        }
    }
}

