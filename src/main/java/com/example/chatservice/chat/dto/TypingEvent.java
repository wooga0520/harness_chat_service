package com.example.chatservice.chat.dto;

public record TypingEvent(
        Long roomId,
        String username,
        String nickname,
        boolean typing
) {
}
