package com.example.chatservice.room.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InviteMembersRequest(
        @NotEmpty List<String> usernames
) {
}
