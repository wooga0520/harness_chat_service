package com.example.chatservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * directUser1Id/directUser2Id are the participant ids of a DM room, always
 * stored with the smaller id first so a (userA, userB) pair always normalizes
 * to the same row regardless of who initiated the DM. Both are null for
 * group rooms. The DB-level unique constraint on the pair is what actually
 * closes the race where two concurrent "start DM" requests both miss the
 * read-then-create check in RoomService and try to create two rooms for the
 * same pair -- Postgres treats (null, null) as distinct per row, so group
 * rooms never collide on this constraint.
 */
@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"direct_user1_id", "direct_user2_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String name;

    @Column(name = "is_group", nullable = false)
    private boolean group;

    @Column(name = "direct_user1_id")
    private Long directUser1Id;

    @Column(name = "direct_user2_id")
    private Long directUser2Id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatRoom(String name, boolean group, Long directUser1Id, Long directUser2Id) {
        this.name = name;
        this.group = group;
        this.directUser1Id = directUser1Id;
        this.directUser2Id = directUser2Id;
    }

    public static ChatRoom newGroupRoom(String name) {
        return ChatRoom.builder().name(name).group(true).build();
    }

    public static ChatRoom newDirectRoom(Long userId1, Long userId2) {
        long lo = Math.min(userId1, userId2);
        long hi = Math.max(userId1, userId2);
        return ChatRoom.builder().group(false).directUser1Id(lo).directUser2Id(hi).build();
    }
}