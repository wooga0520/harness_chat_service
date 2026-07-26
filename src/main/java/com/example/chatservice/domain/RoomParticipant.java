package com.example.chatservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "room_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(nullable = false, columnDefinition = "timestamp default now()")
    private LocalDateTime lastReadAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(10) default 'MEMBER'")
    private ParticipantRole role;

    @Builder
    private RoomParticipant(ChatRoom room, User user, ParticipantRole role) {
        this.room = room;
        this.user = user;
        this.role = role == null ? ParticipantRole.MEMBER : role;
    }

    @PrePersist
    void prePersist() {
        if (lastReadAt == null) {
            lastReadAt = LocalDateTime.now();
        }
    }

    public void markRead() {
        this.lastReadAt = LocalDateTime.now();
    }

    public boolean isOwner() {
        return this.role == ParticipantRole.OWNER;
    }

    public void promoteToOwner() {
        this.role = ParticipantRole.OWNER;
    }
}