package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.chat.dto.PresenceEvent;
import com.example.chatservice.chat.dto.ReadReceiptEvent;
import com.example.chatservice.chat.dto.TypingEvent;
import com.example.chatservice.domain.ChatMessage;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.domain.RoomParticipant;
import com.example.chatservice.domain.User;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomParticipantRepository;
import com.example.chatservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatMessagePublisher chatMessagePublisher;
    private final PresenceRegistry presenceRegistry;
    private final OnlinePresenceService onlinePresenceService;
    private final PresencePublisher presencePublisher;
    private final ReadReceiptPublisher readReceiptPublisher;
    private final SimpMessagingTemplate messagingTemplate;

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

    @Transactional
    public void editMessage(Long roomId, Long messageId, String username, String newContent) {
        ChatMessage message = getOwnedTextMessage(roomId, messageId, username);
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit a deleted message");
        }

        message.edit(newContent);
        chatMessagePublisher.publish(ChatMessageResponse.from(message));
    }

    @Transactional
    public void deleteMessage(Long roomId, Long messageId, String username) {
        ChatMessage message = getOwnedTextMessage(roomId, messageId, username);
        message.delete();
        chatMessagePublisher.publish(ChatMessageResponse.from(message));
    }

    private ChatMessage getOwnedTextMessage(Long roomId, Long messageId, String username) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (!message.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        if (message.getType() != MessageType.TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only text messages can be edited or deleted");
        }
        if (!message.getSender().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the message sender");
        }

        return message;
    }

    /**
     * Remembers which room a STOMP session just entered, so a later
     * SessionDisconnectEvent knows where to announce a LEAVE message.
     */
    public void trackPresence(String sessionId, Long roomId, String username) {
        presenceRegistry.track(sessionId, roomId, username);
    }

    /**
     * Called from the STOMP connect listener. Marks the user online (Redis-backed, shared
     * across instances) and, only on an offline-to-online transition, broadcasts a
     * PresenceEvent to every room the user belongs to.
     */
    @Transactional(readOnly = true)
    public void handleConnect(String sessionId, String username) {
        userRepository.findByUsername(username).ifPresent(user ->
                onlinePresenceService.markOnline(sessionId, user.getId())
                        .ifPresent(userId -> broadcastPresence(user, true)));
    }

    /**
     * Called from the STOMP disconnect listener. Best-effort: if the
     * session had entered a room, broadcast a LEAVE system message for it.
     * Does not remove room membership -- this is presence, not "leaving the
     * group" (see RoomService.leaveRoom for that).
     */
    public void handleDisconnect(String sessionId) {
        presenceRegistry.remove(sessionId).ifPresent(session -> {
            try {
                sendMessage(session.roomId(), session.username(), MessageType.LEAVE, session.username() + "님이 퇴장했습니다.");
            } catch (ResponseStatusException e) {
                // Room or membership may already be gone (e.g. user left the room
                // in another tab before this session disconnected) -- fine to ignore.
            }
        });

        onlinePresenceService.markOffline(sessionId)
                .flatMap(userRepository::findById)
                .ifPresent(user -> broadcastPresence(user, false));
    }

    private void broadcastPresence(User user, boolean online) {
        List<Long> roomIds = roomParticipantRepository.findRoomsByUserId(user.getId()).stream()
                .map(ChatRoom::getId)
                .collect(Collectors.toList());

        if (roomIds.isEmpty()) {
            return;
        }

        presencePublisher.publish(new PresenceEvent(user.getId(), user.getUsername(), user.getNickname(), online, roomIds));
    }

    /**
     * Marks the room read as of now for this user, called when a STOMP session enters
     * the room (see ChatController#enter) or explicitly signals it has read up to now
     * (see ChatController#read). RoomParticipant is a managed entity fetched inside this
     * transaction, so mutating it via markRead() is enough -- no explicit save needed,
     * Hibernate flushes the change on commit. The resulting watermark is broadcast via
     * Redis so every client viewing the room can recompute per-message read counts.
     */
    @Transactional
    public void markRoomRead(Long roomId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room"));

        participant.markRead();

        readReceiptPublisher.publish(
                new ReadReceiptEvent(roomId, user.getId(), user.getUsername(), participant.getLastReadAt()));
    }

    /**
     * Typing indicators are ephemeral and loss-tolerant, so unlike sendMessage
     * they are pushed straight to this instance's local broker instead of
     * round-tripping through Redis: a missed "typing" event self-corrects on
     * the next keystroke or timeout, so cross-instance fanout isn't worth the
     * extra hop.
     */
    public void broadcastTyping(Long roomId, String username, boolean typing) {
        User sender = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, sender.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room");
        }

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/typing",
                new TypingEvent(roomId, sender.getUsername(), sender.getNickname(), typing));
    }
}