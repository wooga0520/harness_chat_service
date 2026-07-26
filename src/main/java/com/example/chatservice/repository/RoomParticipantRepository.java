package com.example.chatservice.repository;

import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, Long> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    Optional<RoomParticipant> findByRoomIdAndUserId(Long roomId, Long userId);

    @Query("select rp from RoomParticipant rp join fetch rp.user where rp.room.id in :roomIds")
    List<RoomParticipant> findByRoomIdIn(@Param("roomIds") List<Long> roomIds);

    @Query("select rp from RoomParticipant rp join fetch rp.user where rp.room.id = :roomId order by rp.joinedAt asc")
    List<RoomParticipant> findByRoomIdOrderByJoinedAtAsc(@Param("roomId") Long roomId);

    @Query("select rp.room from RoomParticipant rp where rp.user.id = :userId order by rp.room.id desc")
    List<ChatRoom> findRoomsByUserId(@Param("userId") Long userId);

    @Query("""
            select rp1.room from RoomParticipant rp1
            join RoomParticipant rp2 on rp1.room = rp2.room
            where rp1.room.group = false
              and rp1.user.id = :userId1
              and rp2.user.id = :userId2
            """)
    Optional<ChatRoom> findDirectRoomBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
