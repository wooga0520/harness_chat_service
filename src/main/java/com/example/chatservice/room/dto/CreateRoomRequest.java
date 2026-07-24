package com.example.chatservice.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateRoomRequest(
        @NotBlank String name,
        @NotEmpty List<String> memberUsernames
) {
}