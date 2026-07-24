package com.example.chatservice.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

/**
 * Tracks logged-out JWTs in Redis until their natural expiry so a stolen/old
 * token can be revoked immediately instead of staying valid until it expires.
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public void blacklist(String token, Date expiresAt) {
        long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofMillis(ttlMillis));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}