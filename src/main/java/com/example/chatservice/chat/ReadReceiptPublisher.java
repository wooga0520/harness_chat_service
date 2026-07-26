package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ReadReceiptEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadReceiptPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic readReceiptEventsTopic;

    public void publish(ReadReceiptEvent event) {
        redisTemplate.convertAndSend(readReceiptEventsTopic.getTopic(), event);
    }
}
