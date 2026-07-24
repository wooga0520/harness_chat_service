package com.example.chatservice.room;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.RoomParticipant;
import com.example.chatservice.domain.User;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomParticipantRepository;
import com.example.chatservice.repository.UserRepository;
import com.example.chatservice.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomResponse createGroupRoom(String creatorUsername, String name, List<String> memberUsernames) {
        User creator = getUser(creatorUsername);

        List<User> members = memberUsernames.stream()
                .distinct()
                .filter(username -> !username.equals(creatorUsername))
                .map(this::getUser)
                .collect(Collectors.toList());
        members.add(creator);

        ChatRoom room = chatRoomRepository.save(ChatRoom.newGroupRoom(name));
        members.forEach(member -> roomParticipantRepository.save(
                RoomParticipant.builder().room(room).user(member).build()));

        return toRoomResponse(room);
    }

    @Transactional
    public RoomResponse getOrCreateDirectRoom(String requesterUsername, String targetUsername) {
        if (requesterUsername.equals(targetUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot DM yourself");
        }

        User requester = getUser(requesterUsername);
        User target = getUser(targetUsername);

        ChatRoom room = roomParticipantRepository.findDirectRoomBetween(requester.getId(), target.getId())
                .orElseGet(() -> {
                    ChatRoom newRoom = chatRoomRepository.save(ChatRoom.newDirectRoom());
                    roomParticipantRepository.save(RoomParticipant.builder().room(newRoom).user(requester).build());
                    roomParticipantRepository.save(RoomParticipant.builder().room(newRoom).user(target).build());
                    return newRoom;
                });

        return toRoomResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listRooms(String username) {
        User user = getUser(username);
        return roomParticipantRepository.findRoomsByUserId(user.getId()).stream()
                .map(this::toRoomResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getMessages(Long roomId, String username, Pageable pageable) {
        User user = getUser(username);

        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room");
        }

        return chatMessageRepository.findByRoomIdOrderBySentAtDesc(roomId, pageable)
                .map(ChatMessageResponse::from);
    }

    private RoomResponse toRoomResponse(ChatRoom room) {
        List<String> nicknames = roomParticipantRepository.findByRoomId(room.getId()).stream()
                .map(participant -> participant.getUser().getNickname())
                .collect(Collectors.toList());
        return RoomResponse.of(room, nicknames);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }
}