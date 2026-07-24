package com.example.chatservice.room.dto;

import com.example.chatservice.domain.ChatRoom;

import java.util.List;

public record RoomResponse(
        Long id,
        String name,
        boolean group,
        List<String> memberNicknames
) {
    public static RoomResponse of(ChatRoom room, List<String> memberNicknames) {
        return new RoomResponse(room.getId(), room.getName(), room.isGroup(), memberNicknames);
    }
}