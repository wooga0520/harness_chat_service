package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.PresenceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Mirrors ChatMessageSubscriber: receives every presence change on every app instance and
 * relays it to the STOMP topic for each room the affected user belongs to, so all connected
 * clients see the online/offline update regardless of which instance the affected user's
 * session lives on.
 */
@Component
@RequiredArgsConstructor
public class PresenceSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final GenericJacksonJsonRedisSerializer serializer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        PresenceEvent event = serializer.deserialize(message.getBody(), PresenceEvent.class);
        event.roomIds().forEach(roomId ->
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/presence", event));
    }
}
