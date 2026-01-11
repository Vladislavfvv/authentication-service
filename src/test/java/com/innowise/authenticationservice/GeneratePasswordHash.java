package com.innowise.authenticationservice;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GeneratePasswordHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "admin";
        
        // Генерируем новый хеш
        String hash = encoder.encode(password);
        System.out.println("Password: " + password);
        System.out.println("New BCrypt Hash: " + hash);
        
        // Проверяем, что хеш соответствует паролю
        boolean matches = encoder.matches(password, hash);
        System.out.println("New hash matches: " + matches);
        
        // Проверяем старый хеш из changelog
        String oldHash1 = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        boolean oldMatches1 = encoder.matches(password, oldHash1);
        System.out.println("Old hash 1 matches: " + oldMatches1);
        
        // Проверяем новый хеш из changelog
        String newHash = "$2a$10$XOPbrlUPQdwdJUpSrIF6X.LbE14qsMmKGhM8A8F6WxT7c1G5J5Qe";
        boolean newMatches = encoder.matches(password, newHash);
        System.out.println("New hash from changelog matches: " + newMatches);
        
        // Генерируем несколько хешей для выбора стабильного
        System.out.println("\n=== Generating multiple hashes ===");
        for (int i = 0; i < 5; i++) {
            String h = encoder.encode(password);
            boolean m = encoder.matches(password, h);
            System.out.println("Hash " + (i+1) + ": " + h + " (matches: " + m + ")");
        }
    }
}

