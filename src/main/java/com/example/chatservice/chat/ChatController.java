package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageRequest;
import com.example.chatservice.chat.dto.TypingRequest;
import com.example.chatservice.domain.MessageType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;

@Controller
@Validated
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/rooms/{roomId}/send")
    public void send(@DestinationVariable Long roomId, @Valid @Payload ChatMessageRequest request, Principal principal) {
        chatService.sendMessage(roomId, principal.getName(), MessageType.TEXT, request.content());
    }

    @MessageMapping("/rooms/{roomId}/enter")
    public void enter(@DestinationVariable Long roomId, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        chatService.trackPresence(headerAccessor.getSessionId(), roomId, principal.getName());
        chatService.markRoomRead(roomId, principal.getName());
        chatService.sendMessage(roomId, principal.getName(), MessageType.ENTER, principal.getName() + "님이 입장했습니다.");
    }

    @MessageMapping("/rooms/{roomId}/typing")
    public void typing(@DestinationVariable Long roomId, @Valid @Payload TypingRequest request, Principal principal) {
        chatService.broadcastTyping(roomId, principal.getName(), request.typing());
    }
}