package com.example.chatservice.room;

import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.room.dto.CreateRoomRequest;
import com.example.chatservice.room.dto.InviteMembersRequest;
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

class RoomMembershipTest extends AbstractIntegrationTest {

    private String signupAndGetToken(String username) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(username, "password1", username))))
                .andExpect(status().isCreated());
        return bearerToken(username);
    }

    private JsonNode getMembers(String bearer, long roomId) throws Exception {
        var result = mockMvc.perform(get("/api/rooms/{roomId}/members", roomId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findMember(JsonNode members, String username) {
        for (JsonNode member : members) {
            if (member.get("username").asText().equals(username)) {
                return member;
            }
        }
        return null;
    }

    @Test
    void creatorIsOwnerAndInvitedMembersAreRegularMembers() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "memAlice" + suffix;
        String bobName = "memBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        signupAndGetToken(bobName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("owner-room", List.of(bobName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        JsonNode members = getMembers(bearerAlice, roomId);
        assertThat(findMember(members, aliceName).get("role").asText()).isEqualTo("OWNER");
        assertThat(findMember(members, bobName).get("role").asText()).isEqualTo("MEMBER");
    }

    @Test
    void anyMemberCanInviteAndTheNewMemberAppearsInTheList() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "invAlice" + suffix;
        String bobName = "invBob" + suffix;
        String carolName = "invCarol" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);
        signupAndGetToken(carolName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("invite-room", List.of(bobName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/rooms/{roomId}/participants", roomId)
                        .header("Authorization", bearerBob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InviteMembersRequest(List.of(carolName)))))
                .andExpect(status().isNoContent());

        JsonNode members = getMembers(bearerAlice, roomId);
        assertThat(findMember(members, carolName)).isNotNull();
    }

    @Test
    void nonOwnerCannotKickAMember() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "kickAlice" + suffix;
        String bobName = "kickBob" + suffix;
        String carolName = "kickCarol" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);
        signupAndGetToken(carolName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("kick-room", List.of(bobName, carolName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();
        long carolId = findMember(getMembers(bearerAlice, roomId), carolName).get("userId").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/{userId}", roomId, carolId)
                        .header("Authorization", bearerBob))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanKickAMemberAndTheyAreRemovedFromTheRoom() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "kickOwnerAlice" + suffix;
        String bobName = "kickOwnerBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("owner-kick-room", List.of(bobName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();
        long bobId = findMember(getMembers(bearerAlice, roomId), bobName).get("userId").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/{userId}", roomId, bobId)
                        .header("Authorization", bearerAlice))
                .andExpect(status().isNoContent());

        assertThat(findMember(getMembers(bearerAlice, roomId), bobName)).isNull();
    }

    @Test
    void ownerCannotKickThemselvesAndMustUseLeaveInstead() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "selfKickAlice" + suffix;
        String bobName = "selfKickBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        signupAndGetToken(bobName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("self-kick-room", List.of(bobName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();
        long aliceId = findMember(getMembers(bearerAlice, roomId), aliceName).get("userId").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/{userId}", roomId, aliceId)
                        .header("Authorization", bearerAlice))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenTheOwnerLeavesOwnershipTransfersToTheEarliestRemainingMember() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String aliceName = "transferAlice" + suffix;
        String bobName = "transferBob" + suffix;
        String bearerAlice = signupAndGetToken(aliceName);
        String bearerBob = signupAndGetToken(bobName);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("transfer-room", List.of(bobName)))))
                .andExpect(status().isCreated())
                .andReturn();
        long roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/rooms/{roomId}/participants/me", roomId)
                        .header("Authorization", bearerAlice))
                .andExpect(status().isNoContent());

        JsonNode members = getMembers(bearerBob, roomId);
        assertThat(findMember(members, bobName).get("role").asText()).isEqualTo("OWNER");
    }
}
