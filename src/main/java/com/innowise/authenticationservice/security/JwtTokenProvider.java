package com.innowise.authenticationservice.security;

import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import com.innowise.authenticationservice.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import lombok.Getter;
//Этот компонент отвечает за создание и валидацию JSON Web Token (JWT). 
//Используется библиотека io.jsonwebtoken (JJWT)
@Component
public class JwtTokenProvider {
    //Симметричный секрет, которым подписываются токены (HS256).    
    @Value("${jwt.secret}")
    private String jwtSecret;

    //Срок действия access токена в миллисекундах.   
    @Getter
    @Value("${jwt.expiration}")
    private long jwtExpiration;

    //Срок действия refresh токена в миллисекундах.    
    @Value("${jwt.refresh.expiration}")
    private long refreshExpiration;

    //Опциональный адрес JWK для интеграции с Keycloak    
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    //Генерация симметричного ключа для подписи токенов.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    //JWT Decoder для Keycloak токенов.
    public JwtDecoder getJwtDecoder() {
        //проверяем Keycloak токены. 
        // используем NimbusJwtDecoder, который скачивает публичные ключи из JWK и валидирует подпись
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        // если Keycloak не подключён (нет JWK URI), 
        //сервис валидирует JWT по старой схеме — HMAC (HS256) через jwt.secret
        return null;
    }

    //Генерация access токена.
    public String generateAccessToken(String username, Role role) {
        return generateToken(username, role, jwtExpiration);
    }
    //Генерация refresh токена.
    public String generateRefreshToken(String username, Role role) {
        return generateToken(username, role, refreshExpiration);
    }

    //Генерация токена.
    private String generateToken(String username, Role role, long jwtExpiration) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role.name())
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //Валидация токена.
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    //Получение username из токена.
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    //Получение роли из токена.
    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("role", String.class);
    }

    //Получение срока действия токена.
    public Date getExpirationDateFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getExpiration();
    }

}
