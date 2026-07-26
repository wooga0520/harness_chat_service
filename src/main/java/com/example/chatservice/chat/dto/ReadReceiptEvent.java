package com.example.chatservice.chat.dto;

import java.time.LocalDateTime;

public record ReadReceiptEvent(
        Long roomId,
        Long userId,
        String username,
        LocalDateTime lastReadAt
) {
}
