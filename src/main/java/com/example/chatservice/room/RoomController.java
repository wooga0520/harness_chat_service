package com.example.chatservice.room;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.room.dto.CreateRoomRequest;
import com.example.chatservice.room.dto.DmRequest;
import com.example.chatservice.room.dto.InviteMembersRequest;
import com.example.chatservice.room.dto.MemberResponse;
import com.example.chatservice.room.dto.RoomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> listRooms(Principal principal) {
        return ResponseEntity.ok(roomService.listRooms(principal.getName()));
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request,
                                                    Principal principal) {
        RoomResponse room = roomService.createGroupRoom(principal.getName(), request.name(), request.memberUsernames());
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @PostMapping("/dm")
    public ResponseEntity<RoomResponse> getOrCreateDm(@Valid @RequestBody DmRequest request, Principal principal) {
        RoomResponse room = roomService.getOrCreateDirectRoom(principal.getName(), request.targetUsername());
        return ResponseEntity.ok(room);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<Page<ChatMessageResponse>> getMessages(
            @PathVariable Long roomId,
            @PageableDefault(size = 30, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable,
            Principal principal) {
        return ResponseEntity.ok(roomService.getMessages(roomId, principal.getName(), pageable));
    }

    @DeleteMapping("/{roomId}/participants/me")
    public ResponseEntity<Void> leaveRoom(@PathVariable Long roomId, Principal principal) {
        roomService.leaveRoom(principal.getName(), roomId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/accept")
    public ResponseEntity<Void> acceptInvite(@PathVariable Long roomId, Principal principal) {
        roomService.acceptInvite(roomId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/decline")
    public ResponseEntity<Void> declineInvite(@PathVariable Long roomId, Principal principal) {
        roomService.declineInvite(roomId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<List<MemberResponse>> getMembers(@PathVariable Long roomId, Principal principal) {
        return ResponseEntity.ok(roomService.getMembers(roomId, principal.getName()));
    }

    @PostMapping("/{roomId}/participants")
    public ResponseEntity<Void> inviteMembers(@PathVariable Long roomId,
                                               @Valid @RequestBody InviteMembersRequest request,
                                               Principal principal) {
        roomService.inviteMembers(roomId, principal.getName(), request.usernames());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/participants/{userId}")
    public ResponseEntity<Void> kickMember(@PathVariable Long roomId, @PathVariable Long userId, Principal principal) {
        roomService.kickMember(roomId, principal.getName(), userId);
        return ResponseEntity.noContent().build();
    }
}