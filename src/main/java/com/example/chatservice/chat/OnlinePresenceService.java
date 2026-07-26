package com.example.chatservice.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks online/offline status per user across all app instances via a Redis set of active
 * STOMP session ids per user (SADD on connect, SREM on disconnect) -- a user counts as online
 * as long as the set is non-empty, which naturally handles multiple tabs/sessions for the same
 * user. The sessionId -> userId lookup used on disconnect is kept in-memory only, since a given
 * session's connect and disconnect events always fire on the same app instance.
 */
@Component
@RequiredArgsConstructor
public class OnlinePresenceService {

    private static final String SESSION_SET_KEY_PREFIX = "presence:sessions:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Long> sessionUserIds = new ConcurrentHashMap<>();

    /**
     * Registers the session as online for the given user. Returns the userId only if this
     * session caused the user to transition from offline to online (i.e. no other session was
     * already open) -- callers should only broadcast a presence change in that case.
     */
    public Optional<Long> markOnline(String sessionId, Long userId) {
        sessionUserIds.put(sessionId, userId);
        String key = SESSION_SET_KEY_PREFIX + userId;
        Long before = redisTemplate.opsForSet().size(key);
        redisTemplate.opsForSet().add(key, sessionId);
        boolean becameOnline = before == null || before == 0;
        return becameOnline ? Optional.of(userId) : Optional.empty();
    }

    /**
     * Unregisters the session. Returns the userId only if this was the user's last open
     * session (i.e. they are now fully offline).
     */
    public Optional<Long> markOffline(String sessionId) {
        Long userId = sessionUserIds.remove(sessionId);
        if (userId == null) {
            return Optional.empty();
        }

        String key = SESSION_SET_KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(key, sessionId);
        Long after = redisTemplate.opsForSet().size(key);
        boolean becameOffline = after == null || after == 0;
        return becameOffline ? Optional.of(userId) : Optional.empty();
    }

    public boolean isOnline(Long userId) {
        Long size = redisTemplate.opsForSet().size(SESSION_SET_KEY_PREFIX + userId);
        return size != null && size > 0;
    }
}
