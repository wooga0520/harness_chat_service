package com.example.chatservice.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ChatService chatService;

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = event.getUser();
        if (user != null) {
            chatService.handleConnect(accessor.getSessionId(), user.getName());
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        chatService.handleDisconnect(event.getSessionId());
    }
}
