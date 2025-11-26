package com.innowise.authenticationservice.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Компонент для хеширования и проверки паролей.
 * Использует алгоритм BCrypt для безопасного хранения паролей в базе данных.
 */
@Component
public class PasswordEncoder {
    // BCrypt encoder для хеширования паролей с автоматической генерацией соли.
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    // Хеширует пароль в открытом виде с использованием BCrypt.
    // Каждый вызов генерирует новый хеш (из-за случайной соли), поэтому нельзя сравнить два хеша напрямую.
    public String encode(String rawPassword) {
        return bCryptPasswordEncoder.encode(rawPassword);
    }

    // Проверяет, соответствует ли пароль в открытом виде сохраненному хешу.
    // Возвращает true, если пароль совпадает, false - если не совпадает.
    public boolean matches(String rawPassword, String encodedPassword) {
        return bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
    }
}
