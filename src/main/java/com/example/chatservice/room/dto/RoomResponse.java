package com.example.chatservice.room.dto;

import com.example.chatservice.domain.ChatMessage;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record RoomResponse(
        Long id,
        String name,
        boolean group,
        List<String> memberNicknames,
        LastMessagePreview lastMessage,
        long unreadCount
) {
    public record LastMessagePreview(
            String content,
            String senderNickname,
            MessageType type,
            LocalDateTime sentAt
    ) {
        public static LastMessagePreview from(ChatMessage message) {
            return new LastMessagePreview(
                    message.getContent(),
                    message.getSender().getNickname(),
                    message.getType(),
                    message.getSentAt());
        }
    }

    public static RoomResponse of(ChatRoom room, List<String> memberNicknames, LastMessagePreview lastMessage, long unreadCount) {
        return new RoomResponse(room.getId(), room.getName(), room.isGroup(), memberNicknames, lastMessage, unreadCount);
    }

    public static RoomResponse ofNew(ChatRoom room, List<String> memberNicknames) {
        return new RoomResponse(room.getId(), room.getName(), room.isGroup(), memberNicknames, null, 0);
    }
}
