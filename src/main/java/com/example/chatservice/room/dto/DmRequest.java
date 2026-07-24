package com.example.chatservice.room.dto;

import jakarta.validation.constraints.NotBlank;

public record DmRequest(
        @NotBlank String targetUsername
) {
}