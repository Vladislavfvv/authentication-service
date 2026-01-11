package com.innowise.authenticationservice.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RegisterRequest {
    @NotBlank(message = "Login is required")
    private String login;

    @NotBlank(message = "Password is required")
    private String password;

    private String role = "ROLE_USER";

    // Опциональные поля для автоматического создания профиля в user-service
    private String firstName;
    private String lastName;

    @PastOrPresent(message = "Birth date cannot be in the future")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    /**
     * Проверяет, указаны ли все необходимые поля для создания профиля.
     */
    public boolean hasProfileData() {
        return firstName != null && !firstName.isBlank() &&
               lastName != null && !lastName.isBlank() &&
               birthDate != null;
    }
}
