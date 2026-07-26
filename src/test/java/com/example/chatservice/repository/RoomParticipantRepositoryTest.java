package com.example.chatservice.repository;

import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.RoomParticipant;
import com.example.chatservice.domain.User;
import com.example.chatservice.support.AbstractRepositoryTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomParticipantRepositoryTest extends AbstractRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private RoomParticipantRepository roomParticipantRepository;

    private User persistUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .password("hash")
                .nickname(username)
                .build());
    }

    @Test
    void findsDirectRoomRegardlessOfArgumentOrder() {
        User alice = persistUser("alice");
        User bob = persistUser("bob");

        ChatRoom dm = chatRoomRepository.save(ChatRoom.newDirectRoom(alice.getId(), bob.getId()));
        roomParticipantRepository.save(RoomParticipant.builder().room(dm).user(alice).build());
        roomParticipantRepository.save(RoomParticipant.builder().room(dm).user(bob).build());

        assertThat(roomParticipantRepository.findDirectRoomBetween(alice.getId(), bob.getId())).contains(dm);
        assertThat(roomParticipantRepository.findDirectRoomBetween(bob.getId(), alice.getId())).contains(dm);
    }

    @Test
    void ignoresGroupRoomsWhenLookingForADirectRoom() {
        User alice = persistUser("alice2");
        User bob = persistUser("bob2");

        ChatRoom group = chatRoomRepository.save(ChatRoom.newGroupRoom("team"));
        roomParticipantRepository.save(RoomParticipant.builder().room(group).user(alice).build());
        roomParticipantRepository.save(RoomParticipant.builder().room(group).user(bob).build());

        assertThat(roomParticipantRepository.findDirectRoomBetween(alice.getId(), bob.getId())).isEmpty();
    }

    @Test
    void doesNotMatchUnrelatedUsers() {
        User alice = persistUser("alice3");
        User bob = persistUser("bob3");
        User carol = persistUser("carol3");

        ChatRoom dm = chatRoomRepository.save(ChatRoom.newDirectRoom(alice.getId(), bob.getId()));
        roomParticipantRepository.save(RoomParticipant.builder().room(dm).user(alice).build());
        roomParticipantRepository.save(RoomParticipant.builder().room(dm).user(bob).build());

        assertThat(roomParticipantRepository.findDirectRoomBetween(alice.getId(), carol.getId())).isEmpty();
    }

    @Test
    void listsRoomsForAUserNewestFirst() {
        User alice = persistUser("alice4");
        User bob = persistUser("bob4");

        ChatRoom first = chatRoomRepository.save(ChatRoom.newGroupRoom("first"));
        ChatRoom second = chatRoomRepository.save(ChatRoom.newGroupRoom("second"));
        roomParticipantRepository.save(RoomParticipant.builder().room(first).user(alice).build());
        roomParticipantRepository.save(RoomParticipant.builder().room(second).user(alice).build());
        roomParticipantRepository.save(RoomParticipant.builder().room(first).user(bob).build());

        List<ChatRoom> rooms = roomParticipantRepository.findRoomsByUserId(alice.getId());

        assertThat(rooms).containsExactly(second, first);
    }
}
