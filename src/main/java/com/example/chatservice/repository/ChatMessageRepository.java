package com.example.chatservice.repository;

import com.example.chatservice.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByRoomIdOrderBySentAtDesc(Long roomId, Pageable pageable);

    interface RoomUnreadCount {
        Long getRoomId();
        long getUnread();
    }

    @Query("""
            select m from ChatMessage m
            join fetch m.sender
            where m.id in (
                select max(m2.id) from ChatMessage m2
                where m2.room.id in :roomIds
                group by m2.room.id
            )
            """)
    List<ChatMessage> findLastMessagesForRooms(@Param("roomIds") List<Long> roomIds);

    @Query("""
            select m.room.id as roomId, count(m) as unread
            from ChatMessage m
            join RoomParticipant rp on rp.room = m.room and rp.user.username = :username
            where m.room.id in :roomIds
              and m.sender.username <> :username
              and m.sentAt > rp.lastReadAt
            group by m.room.id
            """)
    List<RoomUnreadCount> countUnreadByRoomIds(@Param("roomIds") List<Long> roomIds, @Param("username") String username);
}
