package com.example.chatservice.room.dto;

import com.example.chatservice.domain.ParticipantRole;
import com.example.chatservice.domain.RoomParticipant;

import java.time.LocalDateTime;

public record MemberResponse(
        Long userId,
        String username,
        String nickname,
        ParticipantRole role,
        boolean online,
        LocalDateTime lastReadAt
) {
    public static MemberResponse of(RoomParticipant participant, boolean online) {
        return new MemberResponse(
                participant.getUser().getId(),
                participant.getUser().getUsername(),
                participant.getUser().getNickname(),
                participant.getRole(),
                online,
                participant.getLastReadAt()
        );
    }
}
