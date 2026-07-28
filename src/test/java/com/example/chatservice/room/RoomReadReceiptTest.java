package com.example.chatservice.room;

import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.chat.ChatService;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.room.dto.DmRequest;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the read-receipt path end to end: ChatController#enter calls
 * ChatService.markRoomRead, which advances RoomParticipant.lastReadAt so that
 * ChatMessageRepository.countUnreadByRoomIds (see RoomService.toRoomResponses)
 * stops counting older messages as unread. Drives ChatService directly instead of
 * over STOMP -- the STOMP transport itself is already covered by
 * ChatControllerStompValidationTest, so this focuses purely on the read-state logic.
 */
class RoomReadReceiptTest extends AbstractIntegrationTest {

    @Autowired
    private ChatService chatService;

    private String signupAndGetToken(String username) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(username, "password1", username))))
                .andExpect(status().isCreated());
        return bearerToken(username);
    }

    private long unreadCountFor(String bearer, long roomId) throws Exception {
        var result = mockMvc.perform(get("/api/rooms").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rooms = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode room : rooms) {
            if (room.get("id").asLong() == roomId) {
                return room.get("unreadCount").asLong();
            }
        }
        throw new AssertionError("Room " + roomId + " not found in room list");
    }

    @Test
    void markingARoomReadClearsUnreadCountForTheReaderOnly() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "readAlice" + suffix;
        String bobName = "readBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);

        var dmResult = mockMvc.perform(post("/api/rooms/dm")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DmRequest(bobName))))
                .andExpect(status().isOk())
                .andReturn();
        long roomId = objectMapper.readTree(dmResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/rooms/{roomId}/accept", roomId)
                        .header("Authorization", bearerBob))
                .andExpect(status().isNoContent());

        chatService.sendMessage(roomId, aliceName, MessageType.TEXT, "hi bob");
        chatService.sendMessage(roomId, aliceName, MessageType.TEXT, "you there?");

        assertThat(unreadCountFor(bearerBob, roomId)).isEqualTo(2);
        assertThat(unreadCountFor(bearerAlice, roomId)).isZero();

        chatService.markRoomRead(roomId, bobName);

        assertThat(unreadCountFor(bearerBob, roomId)).isZero();
    }
}
