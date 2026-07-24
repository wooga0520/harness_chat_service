package com.example.chatservice.auth;

import com.example.chatservice.auth.dto.LoginRequest;
import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends AbstractIntegrationTest {

    @Test
    void signupThenLoginReturnsAUsableBearerToken() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("neo", "wakeUpNeo", "Neo"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("neo", "wakeUpNeo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void signupWithATakenUsernameIsRejected() throws Exception {
        SignupRequest request = new SignupRequest("trinity", "followTheWhiteRabbit", "Trinity");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("morpheus", "redPillOrBluePill", "Morpheus"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("morpheus", "wrongPassword"))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void logoutBlacklistsTheTokenSoItCanNoLongerAuthenticate() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("cypher", "iWantToBeAgentSmith", "Cypher"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("cypher", "iWantToBeAgentSmith"))))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        String authHeader = "Bearer " + token;

        mockMvc.perform(get("/api/rooms").header("Authorization", authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms").header("Authorization", authHeader))
                .andExpect(status().is4xxClientError());
    }
}
