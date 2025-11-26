package com.innowise.authenticationservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Утилита для генерации BCrypt хешей паролей
 * Используется для создания скриптов инициализации БД
 */
public class HashGenerator {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java HashGenerator <password>");
            System.exit(1);
        }
        
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(args[0]);
        System.out.println("BCrypt hash for '" + args[0] + "':");
        System.out.println(hash);
    }
}

