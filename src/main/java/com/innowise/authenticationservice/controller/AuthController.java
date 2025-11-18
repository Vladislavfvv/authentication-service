package com.innowise.authenticationservice.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.authenticationservice.dto.LoginRequest;
import com.innowise.authenticationservice.dto.RegisterRequest;
import com.innowise.authenticationservice.dto.TokenResponse;
import com.innowise.authenticationservice.dto.TokenValidationRequest;
import com.innowise.authenticationservice.dto.TokenValidationResponse;
import com.innowise.authenticationservice.service.AuthService;

@RestController
@RequestMapping("/auth/v1")
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
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        TokenResponse tokenResponse = authService.register(registerRequest);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/create-token")
    public ResponseEntity<TokenResponse> createToken(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest));
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @Valid @RequestBody TokenValidationRequest request) {
        TokenValidationResponse validationResponse = authService.validateToken(request.getToken());
        return ResponseEntity.ok(validationResponse);
    }
}
