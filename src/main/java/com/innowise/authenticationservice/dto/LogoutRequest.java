package com.innowise.authenticationservice.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank String accessToken,
        @NotBlank String refreshToken
) {
}


