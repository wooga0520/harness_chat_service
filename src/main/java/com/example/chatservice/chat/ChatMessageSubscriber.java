package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Receives every message published on the chat-messages Redis channel, on every app instance
 * (including the one that published it) so all connected WebSocket clients stay in sync
 * regardless of which instance they're attached to.
 *
 * Deserializes with an explicit target type: {@link GenericJacksonJsonRedisSerializer} doesn't
 * embed type info by default, so the untyped deserialize() would otherwise hand back a plain
 * LinkedHashMap instead of a ChatMessageResponse.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final GenericJacksonJsonRedisSerializer serializer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        ChatMessageResponse response = serializer.deserialize(message.getBody(), ChatMessageResponse.class);
        messagingTemplate.convertAndSend("/topic/rooms/" + response.roomId(), response);
    }
}
