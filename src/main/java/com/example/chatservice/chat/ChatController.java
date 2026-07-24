package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageRequest;
import com.example.chatservice.domain.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;

@Controller
@Validated
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/rooms/{roomId}/send")
    public void send(@DestinationVariable Long roomId, @Payload ChatMessageRequest request, Principal principal) {
        chatService.sendMessage(roomId, principal.getName(), MessageType.TEXT, request.content());
    }

    @MessageMapping("/rooms/{roomId}/enter")
    public void enter(@DestinationVariable Long roomId, Principal principal) {
        chatService.sendMessage(roomId, principal.getName(), MessageType.ENTER, principal.getName() + "님이 입장했습니다.");
    }
}