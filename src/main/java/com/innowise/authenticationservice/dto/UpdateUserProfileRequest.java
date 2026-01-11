package com.innowise.authenticationservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserProfileRequest(
        @NotBlank String currentLogin,
        @NotBlank @Email String newLogin,
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}


