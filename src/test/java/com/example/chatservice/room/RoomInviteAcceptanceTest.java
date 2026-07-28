package com.example.chatservice.room;

import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.chat.ChatService;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.room.dto.CreateRoomRequest;
import com.example.chatservice.room.dto.DmRequest;
import com.example.chatservice.room.dto.InviteMembersRequest;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the invite-acceptance gate: knowing a username is no longer enough to force
 * someone into a live chat. A DM target / group inviteMembers invitee is added as PENDING
 * (see RoomService) and ChatService.sendMessage rejects sends until they accept.
 */
class RoomInviteAcceptanceTest extends AbstractIntegrationTest {

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

    private JsonNode roomList(String bearer) throws Exception {
        var result = mockMvc.perform(get("/api/rooms").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findRoom(String bearer, long roomId) throws Exception {
        for (JsonNode room : roomList(bearer)) {
            if (room.get("id").asLong() == roomId) {
                return room;
            }
        }
        return null;
    }

    @Test
    void neitherSideCanSendInADmUntilTheTargetAccepts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "acceptAlice" + suffix;
        String bobName = "acceptBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        signupAndGetToken(bobName);

        var dmResult = mockMvc.perform(post("/api/rooms/dm")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DmRequest(bobName))))
                .andExpect(status().isOk())
                .andReturn();
        long roomId = objectMapper.readTree(dmResult.getResponse().getContentAsString()).get("id").asLong();

        assertThat(findRoom(bearerAlice, roomId).get("pendingForMe").asBoolean()).isFalse();
        assertThat(findRoom(bearerAlice, roomId).get("active").asBoolean()).isFalse();

        assertThatThrownBy(() -> chatService.sendMessage(roomId, aliceName, MessageType.TEXT, "hi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> chatService.sendMessage(roomId, bobName, MessageType.TEXT, "hi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        mockMvc.perform(post("/api/rooms/{roomId}/accept", roomId)
                        .header("Authorization", bearerToken(bobName)))
                .andExpect(status().isNoContent());

        assertThat(findRoom(bearerAlice, roomId).get("active").asBoolean()).isTrue();

        chatService.sendMessage(roomId, aliceName, MessageType.TEXT, "hi bob");
        chatService.sendMessage(roomId, bobName, MessageType.TEXT, "hi alice");
    }

    @Test
    void decliningADmDeletesItAndAFreshDmCanBeStartedAfterward() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "declineAlice" + suffix;
        String bobName = "declineBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);

        var dmResult = mockMvc.perform(post("/api/rooms/dm")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DmRequest(bobName))))
                .andExpect(status().isOk())
                .andReturn();
        long roomId = objectMapper.readTree(dmResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/rooms/{roomId}/decline", roomId)
                        .header("Authorization", bearerBob))
                .andExpect(status().isNoContent());

        assertThat(findRoom(bearerAlice, roomId)).isNull();
        assertThat(findRoom(bearerBob, roomId)).isNull();

        var secondDmResult = mockMvc.perform(post("/api/rooms/dm")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DmRequest(bobName))))
                .andExpect(status().isOk())
                .andReturn();
        long secondRoomId = objectMapper.readTree(secondDmResult.getResponse().getContentAsString()).get("id").asLong();
        assertThat(secondRoomId).isNotEqualTo(roomId);
    }

    @Test
    void invitedGroupMemberCannotSendUntilAcceptingButExistingMembersAreUnaffected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "grpAcceptAlice" + suffix;
        String bobName = "grpAcceptBob" + suffix;
        String carolName = "grpAcceptCarol" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);
        signupAndGetToken(carolName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("accept-room", List.of(bobName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/rooms/{roomId}/participants", roomId)
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteMembersRequest(List.of(carolName)))))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> chatService.sendMessage(roomId, carolName, MessageType.TEXT, "hi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        chatService.sendMessage(roomId, aliceName, MessageType.TEXT, "still talking without carol");
        chatService.sendMessage(roomId, bobName, MessageType.TEXT, "yep");

        mockMvc.perform(post("/api/rooms/{roomId}/accept", roomId)
                        .header("Authorization", bearerToken(carolName)))
                .andExpect(status().isNoContent());

        chatService.sendMessage(roomId, carolName, MessageType.TEXT, "hi, now I can talk");
    }

    @Test
    void pendingInviteeMustDeclineNotLeave() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "declineGrpAlice" + suffix;
        String bobName = "declineGrpBob" + suffix;
        String carolName = "declineGrpCarol" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        signupAndGetToken(bobName);
        signupAndGetToken(carolName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("decline-room", List.of(carolName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/rooms/{roomId}/participants", roomId)
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteMembersRequest(List.of(bobName)))))
                .andExpect(status().isNoContent());

        String bearerBob = bearerToken(bobName);

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/me", roomId)
                        .header("Authorization", bearerBob))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/rooms/{roomId}/decline", roomId)
                        .header("Authorization", bearerBob))
                .andExpect(status().isNoContent());

        assertThat(findRoom(bearerBob, roomId)).isNull();
    }
}
