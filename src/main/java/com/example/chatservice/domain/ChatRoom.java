package com.example.chatservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatRoom(String name, boolean group) {
        this.name = name;
        this.group = group;
    }

    public static ChatRoom newGroupRoom(String name) {
        return ChatRoom.builder().name(name).group(true).build();
    }

    public static ChatRoom newDirectRoom() {
        return ChatRoom.builder().group(false).build();
    }
}