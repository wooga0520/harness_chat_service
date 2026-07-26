package com.example.chatservice.room;

import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.room.dto.CreateRoomRequest;
import com.example.chatservice.room.dto.DmRequest;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoomLeaveTest extends AbstractIntegrationTest {

    private String signupAndGetToken(String username) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(username, "password1", username))))
                .andExpect(status().isCreated());
        return bearerToken(username);
    }

    private JsonNode roomList(String bearer) throws Exception {
        var result = mockMvc.perform(get("/api/rooms").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean roomListContains(String bearer, long roomId) throws Exception {
        for (JsonNode room : roomList(bearer)) {
            if (room.get("id").asLong() == roomId) {
                return true;
            }
        }
        return false;
    }

    @Test
    void memberCanLeaveAGroupRoomAndTheRestSeeAnUpdatedMemberListAndLeaveMessage() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "leaveAlice" + suffix;
        String bobName = "leaveBob" + suffix;
        String carolName = "leaveCarol" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);
        signupAndGetToken(carolName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("leave-room", List.of(bobName, carolName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/me", roomId)
                        .header("Authorization", bearerBob))
                .andExpect(status().isNoContent());

        assertThat(roomListContains(bearerBob, roomId)).isFalse();

        JsonNode aliceRooms = roomList(bearerAlice);
        JsonNode aliceRoom = null;
        for (JsonNode room : aliceRooms) {
            if (room.get("id").asLong() == roomId) {
                aliceRoom = room;
            }
        }
        assertThat(aliceRoom).isNotNull();
        List<String> memberNicknames = objectMapper.convertValue(aliceRoom.get("memberNicknames"), List.class);
        assertThat(memberNicknames).containsExactlyInAnyOrder(aliceName, carolName);

        var messagesResult = mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .header("Authorization", bearerAlice))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode messages = objectMapper.readTree(messagesResult.getResponse().getContentAsString()).get("content");
        boolean hasLeaveMessage = false;
        for (JsonNode message : messages) {
            if ("LEAVE".equals(message.get("type").asText()) && message.get("content").asText().contains(bobName)) {
                hasLeaveMessage = true;
            }
        }
        assertThat(hasLeaveMessage).isTrue();
    }

    @Test
    void leavingADirectMessageRoomIsRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "dmLeaveAlice" + suffix;
        String bobName = "dmLeaveBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        signupAndGetToken(bobName);

        var dmResult = mockMvc.perform(post("/api/rooms/dm")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DmRequest(bobName))))
                .andExpect(status().isOk())
                .andReturn();
        long roomId = objectMapper.readTree(dmResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/me", roomId)
                        .header("Authorization", bearerAlice))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonParticipantCannotLeaveAGroupRoom() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "outsiderAlice" + suffix;
        String memberName = "outsiderMember" + suffix;
        String outsiderName = "outsider" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        signupAndGetToken(memberName);
        String bearerOutsider = signupAndGetToken(outsiderName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("private-room", List.of(memberName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/me", roomId)
                        .header("Authorization", bearerOutsider))
                .andExpect(status().isForbidden());
    }

    @Test
    void leavingANonexistentRoomIs404() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String bearer = signupAndGetToken("ghostRoomUser" + suffix);

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/me", 999_999L)
                        .header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }
}
