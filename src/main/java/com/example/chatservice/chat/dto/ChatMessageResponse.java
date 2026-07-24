package com.example.chatservice.chat.dto;

import com.example.chatservice.domain.ChatMessage;
import com.example.chatservice.domain.MessageType;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long senderId,
        String senderUsername,
        String senderNickname,
        MessageType type,
        String content,
        LocalDateTime sentAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoom().getId(),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getSender().getNickname(),
                message.getType(),
                message.getContent(),
                message.getSentAt()
        );
    }
}