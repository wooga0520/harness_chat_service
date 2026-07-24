package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.domain.ChatMessage;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.domain.User;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomParticipantRepository;
import com.example.chatservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatMessagePublisher chatMessagePublisher;

    @Transactional
    public void sendMessage(Long roomId, String username, MessageType type, String content) {
        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, sender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room");
        }

        ChatMessage message = ChatMessage.builder()
                .room(room)
                .sender(sender)
                .type(type)
                .content(content)
                .build();
        chatMessageRepository.save(message);

        chatMessagePublisher.publish(ChatMessageResponse.from(message));
    }
}