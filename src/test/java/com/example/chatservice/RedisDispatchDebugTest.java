package com.example.chatservice;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisDispatchDebugTest extends AbstractIntegrationTest {

    @Autowired
    private GenericJacksonJsonRedisSerializer serializer;

    @Test
    void typedDeserializeRoundTrip() {
        ChatMessageResponse resp = new ChatMessageResponse(1L, 2L, 3L, "user123", "nick", MessageType.TEXT, "hello world", LocalDateTime.now());

        byte[] bytes = serializer.serialize(resp);
        ChatMessageResponse deserialized = serializer.deserialize(bytes, ChatMessageResponse.class);

        assertEquals(resp, deserialized);
        System.out.println("=== ROUND TRIP OK === " + deserialized);
    }
}
