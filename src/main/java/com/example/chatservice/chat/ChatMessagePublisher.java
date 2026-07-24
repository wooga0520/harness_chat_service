package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic chatMessagesTopic;

    public void publish(ChatMessageResponse message) {
        redisTemplate.convertAndSend(chatMessagesTopic.getTopic(), message);
    }
}