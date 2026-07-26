package com.example.chatservice.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ChatService chatService;

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        chatService.handleDisconnect(event.getSessionId());
    }
}
