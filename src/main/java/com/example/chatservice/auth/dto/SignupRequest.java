package com.example.chatservice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 4, max = 100) String password,
        @NotBlank @Size(min = 1, max = 50) String nickname
) {
}