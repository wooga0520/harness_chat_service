package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ReadReceiptEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Mirrors ChatMessageSubscriber: relays every read-watermark update to the room's read topic
 * on every app instance, so clients recompute per-message "읽음" counts regardless of which
 * instance the reading user is connected to.
 */
@Component
@RequiredArgsConstructor
public class ReadReceiptSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final GenericJacksonJsonRedisSerializer serializer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        ReadReceiptEvent event = serializer.deserialize(message.getBody(), ReadReceiptEvent.class);
        messagingTemplate.convertAndSend("/topic/rooms/" + event.roomId() + "/read", event);
    }
}
