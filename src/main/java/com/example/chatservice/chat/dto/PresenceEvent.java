package com.example.chatservice.chat.dto;

import java.util.List;

public record PresenceEvent(
        Long userId,
        String username,
        String nickname,
        boolean online,
        List<Long> roomIds
) {
}
