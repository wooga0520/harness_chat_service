package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.PresenceEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresencePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic presenceEventsTopic;

    public void publish(PresenceEvent event) {
        redisTemplate.convertAndSend(presenceEventsTopic.getTopic(), event);
    }
}
