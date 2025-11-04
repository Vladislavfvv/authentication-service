package com.innowise.authenticationservice.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestParam String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

//    @PostMapping("/validate")
//    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody String request) {
//        return ResponseEntity.ok(authService.validateToken(request));
//    }

//    @PostMapping("/validate")
//    public ResponseEntity<TokenValidationResponse> validateToken(
//            @RequestHeader("Authorization") String authHeader) {
//
//        String token = extractTokenFromHeader(authHeader); // "Bearer {token}"
//        TokenValidationResponse validationResponse = authService.validateToken(token);
//        return ResponseEntity.ok(validationResponse);
//    }
//
//    private String extractTokenFromHeader(String authHeader) {
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            return authHeader.substring(7);
//        }
//        throw new RuntimeException("Invalid Authorization header");
//    }

    // Вариант 1: Через Request Body (рекомендуется)
    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @Valid @RequestBody TokenValidationRequest request) {

        TokenValidationResponse validationResponse = authService.validateToken(request.getToken());
        return ResponseEntity.ok(validationResponse);
    }

    // Вариант 2: Через Header (альтернатива)
    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(new TokenValidationResponse(false, null, null));
        }

        String token = authHeader.substring(7);
        TokenValidationResponse validationResponse = authService.validateToken(token);
        return ResponseEntity.ok(validationResponse);
    }
}
