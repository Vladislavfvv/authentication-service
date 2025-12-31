package com.innowise.authenticationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserServiceClient userServiceClient;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByLogin(loginRequest.getLogin())
                .orElseThrow(() -> new AuthenticationException("Invalid login or password"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid login or password");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getLogin(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getLogin(), user.getRole());

        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getJwtExpiration());
    }

    /**
     * Регистрирует пользователя в auth_db и возвращает токены.
     * Создает учетные данные в auth_db и сразу выдает JWT токены.
     * 
     * Если в запросе указаны firstName, lastName, birthDate - автоматически создает профиль в user-service.
     * Если эти поля не указаны - создаются только credentials, профиль можно создать позже через /api/v1/users/createUser.
     * 
     * @param registerRequest данные для регистрации (login, password, role, и опционально firstName, lastName, birthDate)
     * @return TokenResponse с access и refresh токенами
     */
    public TokenResponse register(RegisterRequest registerRequest) {
        // Если пользователь уже существует, проверяем пароль и возвращаем токен (как login)
        if (userRepository.existsByLogin(registerRequest.getLogin())) {
            log.info("User {} already exists, attempting login instead of registration", registerRequest.getLogin());
            // Используем логику login для существующего пользователя
            User existingUser = userRepository.findByLogin(registerRequest.getLogin())
                    .orElseThrow(() -> new AuthenticationException("User not found"));
            
            // Проверяем пароль
            if (!passwordEncoder.matches(registerRequest.getPassword(), existingUser.getPasswordHash())) {
                throw new AuthenticationException("Invalid login or password");
            }
            
            // Если указаны данные профиля и пользователь еще не имеет профиля в user-service,
            // пытаемся создать профиль (но не прерываем процесс, если не удалось)
            if (registerRequest.hasProfileData()) {
                log.info("Profile data provided for existing user, attempting to create/update profile in user-service");
                try {
                    userServiceClient.createUser(
                        existingUser.getLogin(),
                        registerRequest.getFirstName(),
                        registerRequest.getLastName(),
                        registerRequest.getBirthDate()
                    );
                    log.info("User profile created/updated successfully in user-service for: {}", existingUser.getLogin());
                } catch (Exception e) {
                    log.warn("Failed to create/update user profile in user-service for existing user: {}. Error: {}", 
                        existingUser.getLogin(), e.getMessage());
                    // Не прерываем процесс, продолжаем выдачу токена
                }
            }
            
            // Возвращаем токены для существующего пользователя
            String accessToken = jwtTokenProvider.generateAccessToken(existingUser.getLogin(), existingUser.getRole());
            String refreshToken = jwtTokenProvider.generateRefreshToken(existingUser.getLogin(), existingUser.getRole());
            return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getJwtExpiration());
        }

        // Валидация роли
        // Поддерживаем как "USER"/"ADMIN", так и "ROLE_USER"/"ROLE_ADMIN"
        String roleStr = registerRequest.getRole().toUpperCase().trim();
        if (!roleStr.startsWith("ROLE_")) {
            roleStr = "ROLE_" + roleStr;
        }
        
        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid role: " + registerRequest.getRole() + 
                    ". Valid values: USER, ADMIN, ROLE_USER, ROLE_ADMIN");
        }

        // Запрещаем создавать ADMIN через публичный endpoint
        if (role == Role.ROLE_ADMIN) {
            throw new AuthenticationException("Cannot register with ADMIN role");
        }

        String passwordHash = passwordEncoder.encode(registerRequest.getPassword());

        User user = new User(registerRequest.getLogin(), passwordHash, role);

        userRepository.save(user);
        
        // Сразу выдаем токены после регистрации
        String accessToken = jwtTokenProvider.generateAccessToken(user.getLogin(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getLogin(), user.getRole());
        
        // Если указаны данные профиля - автоматически создаем профиль в user-service
        if (registerRequest.hasProfileData()) {
            log.info("Profile data provided, creating user profile in user-service for: {}", user.getLogin());
            try {
                userServiceClient.createUser(
                    user.getLogin(),
                    registerRequest.getFirstName(),
                    registerRequest.getLastName(),
                    registerRequest.getBirthDate()
                );
                log.info("User profile created successfully in user-service for: {}", user.getLogin());
            } catch (Exception e) {
                log.error("Failed to create user profile in user-service for: {}. Error: {}", 
                    user.getLogin(), e.getMessage(), e);
                // Не прерываем регистрацию, если не удалось создать профиль
                // Пользователь может создать профиль позже вручную
            }
        } else {
            log.info("No profile data provided, user can create profile later using token");
        }
        
        return new TokenResponse(accessToken, refreshToken, jwtTokenProvider.getJwtExpiration());
    }

    /**
     * Обновляет access токен с помощью refresh токена.
     * Валидирует refresh токен, извлекает информацию о пользователе и генерирует новую пару токенов.
     * 
     * @param refreshToken refresh токен для обновления
     * @return TokenResponse с новыми access и refresh токенами
     * @throws AuthenticationException если токен невалиден, роль некорректна или пользователь не найден
     */
    public TokenResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        String roleStr = jwtTokenProvider.getRoleFromToken(refreshToken);
        
        // Валидация роли из токена
        Role roleFromToken;
        try {
            roleFromToken = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid role in refresh token: " + roleStr);
        }

        User user = userRepository.findByLogin(username)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        // Проверяем, что роль в токене совпадает с ролью в БД (дополнительная проверка безопасности)
        if (user.getRole() != roleFromToken) {
            throw new AuthenticationException("Role mismatch: token role does not match user role");
        }

        // Используем роль из БД для генерации новых токенов
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getLogin(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getLogin(), user.getRole());

        return new TokenResponse(newAccessToken, newRefreshToken, jwtTokenProvider.getJwtExpiration());
    }


    public TokenValidationResponse validateToken(String token) {
        try {
            if (jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);
                return new TokenValidationResponse(true, username, role);
            }
            return new TokenValidationResponse(false, null, null);
        } catch (Exception e) {
            return new TokenValidationResponse(false, null, null);
        }
    }

    /**
     * Удаляет пользователя по email (login) из auth_db.
     * Используется для синхронизации с user-service при удалении пользователя.
     * 
     * @param email email (login) пользователя для удаления
     * @throws AuthenticationException если пользователь не найден
     */
    @Transactional
    public void deleteUserByEmail(String email) {
        User user = userRepository.findByLogin(email)
                .orElseThrow(() -> new AuthenticationException("User with email " + email + " not found"));
        
        userRepository.delete(user);
    }
}
