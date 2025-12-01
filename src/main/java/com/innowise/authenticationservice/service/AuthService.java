package com.innowise.authenticationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@AllArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

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
     * После регистрации пользователь должен самостоятельно создать профиль в user-service,
     * используя полученный токен (email будет извлечен из токена).
     * 
     * @param registerRequest данные для регистрации (login, password, role)
     * @return TokenResponse с access и refresh токенами
     */
    public TokenResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByLogin(registerRequest.getLogin())) {
            throw new AuthenticationException("Login already exists");
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
        // Пользователь должен самостоятельно создать профиль в user-service, используя полученный токен
        String accessToken = jwtTokenProvider.generateAccessToken(user.getLogin(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getLogin(), user.getRole());
        
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
