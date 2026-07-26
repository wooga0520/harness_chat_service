package com.example.chatservice.room;

import com.example.chatservice.chat.ChatService;
import com.example.chatservice.chat.OnlinePresenceService;
import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.domain.ParticipantRole;
import com.example.chatservice.domain.RoomParticipant;
import com.example.chatservice.domain.User;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomParticipantRepository;
import com.example.chatservice.repository.UserRepository;
import com.example.chatservice.room.dto.MemberResponse;
import com.example.chatservice.room.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final ChatService chatService;
    private final OnlinePresenceService onlinePresenceService;

    @Transactional
    public RoomResponse createGroupRoom(String creatorUsername, String name, List<String> memberUsernames) {
        User creator = getUser(creatorUsername);

        List<User> members = memberUsernames.stream()
                .distinct()
                .filter(username -> !username.equals(creatorUsername))
                .map(this::getUser)
                .collect(Collectors.toList());

        ChatRoom room = chatRoomRepository.save(ChatRoom.newGroupRoom(name));
        roomParticipantRepository.save(
                RoomParticipant.builder().room(room).user(creator).role(ParticipantRole.OWNER).build());
        members.forEach(member -> roomParticipantRepository.save(
                RoomParticipant.builder().room(room).user(member).role(ParticipantRole.MEMBER).build()));

        return toRoomResponse(room, creatorUsername);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(Long roomId, String username) {
        User user = getUser(username);
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room");
        }

        return roomParticipantRepository.findByRoomIdOrderByJoinedAtAsc(roomId).stream()
                .map(participant -> MemberResponse.of(participant, onlinePresenceService.isOnline(participant.getUser().getId())))
                .collect(Collectors.toList());
    }

    /**
     * Any existing member may invite new members (no owner restriction). Usernames that
     * are already participants or duplicate the requester are silently skipped rather than
     * erroring, since a race with another invite/DM-creation shouldn't fail the whole call.
     */
    @Transactional
    public void inviteMembers(Long roomId, String requesterUsername, List<String> usernames) {
        User requester = getUser(requesterUsername);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.isGroup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot invite members to a direct message room");
        }
        if (!roomParticipantRepository.existsByRoomIdAndUserId(roomId, requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room");
        }

        List<User> invitees = usernames.stream()
                .distinct()
                .filter(u -> !u.equals(requesterUsername))
                .map(this::getUser)
                .filter(u -> !roomParticipantRepository.existsByRoomIdAndUserId(roomId, u.getId()))
                .collect(Collectors.toList());

        if (invitees.isEmpty()) {
            return;
        }

        invitees.forEach(invitee -> roomParticipantRepository.save(
                RoomParticipant.builder().room(room).user(invitee).role(ParticipantRole.MEMBER).build()));

        String nicknames = invitees.stream().map(User::getNickname).collect(Collectors.joining(", "));
        chatService.sendMessage(roomId, requesterUsername, MessageType.INVITE,
                requesterUsername + "님이 " + nicknames + "님을 초대했습니다.");
    }

    /**
     * Only the room owner can remove another member. Kicking yourself isn't supported here
     * -- use the leave endpoint instead, which also handles ownership transfer.
     */
    @Transactional
    public void kickMember(Long roomId, String requesterUsername, Long targetUserId) {
        User requester = getUser(requesterUsername);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.isGroup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove members from a direct message room");
        }
        if (requester.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the leave endpoint to remove yourself");
        }

        RoomParticipant requesterParticipant = roomParticipantRepository.findByRoomIdAndUserId(roomId, requester.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room"));
        if (!requesterParticipant.isOwner()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room owner can remove members");
        }

        RoomParticipant targetParticipant = roomParticipantRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a member of this room"));

        String targetNickname = targetParticipant.getUser().getNickname();
        roomParticipantRepository.delete(targetParticipant);

        chatService.sendMessage(roomId, requesterUsername, MessageType.KICK,
                targetNickname + "님이 " + requesterUsername + "님에 의해 방에서 제외되었습니다.");
    }

    @Transactional
    public RoomResponse getOrCreateDirectRoom(String requesterUsername, String targetUsername) {
        if (requesterUsername.equals(targetUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot DM yourself");
        }

        User requester = getUser(requesterUsername);
        User target = getUser(targetUsername);

        ChatRoom room = roomParticipantRepository.findDirectRoomBetween(requester.getId(), target.getId())
                .orElseGet(() -> createDirectRoom(requester, target));

        return toRoomResponse(room, requesterUsername);
    }

    /**
     * Runs in its own REQUIRES_NEW transaction so a unique-constraint violation on
     * (direct_user1_id, direct_user2_id) only rolls back this insert attempt, not the
     * outer request transaction -- letting us fall back to re-reading the room that a
     * concurrent request just created instead of the whole request failing.
     */
    private ChatRoom createDirectRoom(User requester, User target) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        try {
            return requiresNew.execute(status -> {
                ChatRoom newRoom = chatRoomRepository.saveAndFlush(ChatRoom.newDirectRoom(requester.getId(), target.getId()));
                roomParticipantRepository.save(RoomParticipant.builder().room(newRoom).user(requester).build());
                roomParticipantRepository.save(RoomParticipant.builder().room(newRoom).user(target).build());
                return newRoom;
            });
        } catch (DataIntegrityViolationException e) {
            return roomParticipantRepository.findDirectRoomBetween(requester.getId(), target.getId())
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Only group rooms support leaving -- a DM room's membership is fixed to the two
     * users the (direct_user1_id, direct_user2_id) pair identifies it by, so "leaving"
     * has no defined meaning there. Broadcasts the LEAVE message before deleting the
     * RoomParticipant row, since ChatService.sendMessage checks participation and would
     * otherwise reject the leaving user's own departure notice.
     */
    @Transactional
    public void leaveRoom(String username, Long roomId) {
        User user = getUser(username);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.isGroup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot leave a direct message room");
        }

        RoomParticipant participant = roomParticipantRepository.findByRoomIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this room"));

        chatService.sendMessage(roomId, username, MessageType.LEAVE, username + "님이 방을 나갔습니다.");

        if (participant.isOwner()) {
            roomParticipantRepository.findByRoomIdOrderByJoinedAtAsc(roomId).stream()
                    .filter(p -> !p.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .ifPresent(RoomParticipant::promoteToOwner);
        }

        roomParticipantRepository.delete(participant);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> listRooms(String username) {
        User user = getUser(username);
        List<ChatRoom> rooms = roomParticipantRepository.findRoomsByUserId(user.getId());
        return toRoomResponses(rooms, username);
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

    private RoomResponse toRoomResponse(ChatRoom room, String username) {
        return toRoomResponses(List.of(room), username).get(0);
    }

    private List<RoomResponse> toRoomResponses(List<ChatRoom> rooms, String username) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).collect(Collectors.toList());

        Map<Long, List<String>> nicknamesByRoom = roomParticipantRepository.findByRoomIdIn(roomIds).stream()
                .collect(Collectors.groupingBy(
                        participant -> participant.getRoom().getId(),
                        Collectors.mapping(participant -> participant.getUser().getNickname(), Collectors.toList())));

        Map<Long, RoomResponse.LastMessagePreview> lastMessageByRoom = chatMessageRepository.findLastMessagesForRooms(roomIds).stream()
                .collect(Collectors.toMap(message -> message.getRoom().getId(), RoomResponse.LastMessagePreview::from));

        Map<Long, Long> unreadByRoom = chatMessageRepository.countUnreadByRoomIds(roomIds, username).stream()
                .collect(Collectors.toMap(ChatMessageRepository.RoomUnreadCount::getRoomId, ChatMessageRepository.RoomUnreadCount::getUnread));

        return rooms.stream()
                .map(room -> RoomResponse.of(
                        room,
                        nicknamesByRoom.getOrDefault(room.getId(), List.of()),
                        lastMessageByRoom.get(room.getId()),
                        unreadByRoom.getOrDefault(room.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }
}