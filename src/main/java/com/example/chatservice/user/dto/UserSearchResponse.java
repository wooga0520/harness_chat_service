package com.example.chatservice.user.dto;

import com.example.chatservice.domain.User;

public record UserSearchResponse(
        Long id,
        String username,
        String nickname
) {
    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(user.getId(), user.getUsername(), user.getNickname());
    }
}
