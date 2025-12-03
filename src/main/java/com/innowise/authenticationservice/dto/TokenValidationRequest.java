package com.innowise.authenticationservice.dto;

// DTO для запроса валидации

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TokenValidationRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
