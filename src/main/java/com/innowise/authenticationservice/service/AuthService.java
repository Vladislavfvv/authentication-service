package com.innowise.authenticationservice.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final Optional<KeycloakService> keycloakService;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,//кодирует пароли пользователей
                       JwtTokenProvider jwtTokenProvider,//генерирует и валидирует JWT токены
                       Optional<KeycloakService> keycloakService) {//сервис для интеграции с Keycloak
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.keycloakService = keycloakService;
    }

    
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


    public void register(RegisterRequest registerRequest) {
        if (userRepository.existsByLogin(registerRequest.getLogin())) {
            throw new AuthenticationException("Login already exists");
        }

        // Валидация роли
        Role role;
        try {
            role = Role.valueOf(registerRequest.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException("Invalid role: " + registerRequest.getRole());
        }

        // Запрещаем создавать ADMIN через публичный endpoint
        if (role == Role.ROLE_ADMIN) {
            throw new AuthenticationException("Cannot register with ADMIN role");
        }

        String passwordHash = passwordEncoder.encode(registerRequest.getPassword());//кодируем пароль пользователя

        User user = new User(registerRequest.getLogin(), passwordHash, role);

        userRepository.save(user);
        log.info("Registered user: " + user.getLogin());
        // Создание пользователя в Keycloak (если Keycloak доступен)
        keycloakService.ifPresent(service -> {
            try {
                service.createUser(registerRequest.getLogin(), registerRequest.getPassword(), role);
            } catch (Exception e) {
                log.error("Failed to create user {} in Keycloak: {}", registerRequest.getLogin(), e.getMessage(), e);
            }
        });
    }

    public TokenResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthenticationException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        String roleStr = jwtTokenProvider.getRoleFromToken(refreshToken);
        Role role = Role.valueOf(roleStr);

        User user = userRepository.findByLogin(username)
                .orElseThrow(() -> new AuthenticationException("User not found"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getLogin(), role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getLogin(), role);

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
}
