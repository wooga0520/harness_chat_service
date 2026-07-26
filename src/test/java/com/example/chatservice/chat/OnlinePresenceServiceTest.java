package com.example.chatservice.chat;

import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Redis-backed ref-counting directly against the real (containerized) Redis
 * instance, since the online/offline transition logic only matters when multiple sessions
 * for the same user are involved -- a mocked RedisTemplate would just echo back whatever the
 * test stubs it to return.
 */
class OnlinePresenceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OnlinePresenceService onlinePresenceService;

    @Test
    void firstSessionMarksUserOnlineAndLastSessionMarksThemOffline() {
        Long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String sessionA = "session-" + UUID.randomUUID();
        String sessionB = "session-" + UUID.randomUUID();

        assertThat(onlinePresenceService.isOnline(userId)).isFalse();

        assertThat(onlinePresenceService.markOnline(sessionA, userId)).contains(userId);
        assertThat(onlinePresenceService.isOnline(userId)).isTrue();

        // A second session for the same user shouldn't re-trigger an online broadcast.
        assertThat(onlinePresenceService.markOnline(sessionB, userId)).isEmpty();
        assertThat(onlinePresenceService.isOnline(userId)).isTrue();

        // Closing one of two sessions keeps the user online.
        assertThat(onlinePresenceService.markOffline(sessionA)).isEmpty();
        assertThat(onlinePresenceService.isOnline(userId)).isTrue();

        // Closing the last session takes them offline.
        assertThat(onlinePresenceService.markOffline(sessionB)).contains(userId);
        assertThat(onlinePresenceService.isOnline(userId)).isFalse();
    }

    @Test
    void markOfflineForAnUnknownSessionIsANoOp() {
        assertThat(onlinePresenceService.markOffline("never-registered-" + UUID.randomUUID())).isEmpty();
    }
}
