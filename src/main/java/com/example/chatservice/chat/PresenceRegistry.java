package com.example.chatservice.chat;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which room a STOMP session most recently entered, so that a
 * SessionDisconnectEvent (tab closed, network drop, etc.) can announce a
 * LEAVE system message without requiring the client to send an explicit
 * "leave" frame first. Keyed by STOMP session id, which is local to this
 * app instance -- this is intentionally in-memory only, not shared via Redis.
 */
@Component
public class PresenceRegistry {

    public record RoomSession(Long roomId, String username) {
    }

    private final Map<String, RoomSession> sessionRooms = new ConcurrentHashMap<>();

    public void track(String sessionId, Long roomId, String username) {
        sessionRooms.put(sessionId, new RoomSession(roomId, username));
    }

    public Optional<RoomSession> remove(String sessionId) {
        return Optional.ofNullable(sessionRooms.remove(sessionId));
    }
}
