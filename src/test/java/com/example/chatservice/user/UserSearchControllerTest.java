package com.example.chatservice.user;

import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSearchControllerTest extends AbstractIntegrationTest {

    private String signupAndGetToken(String username, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest(username, "password1", nickname))))
                .andExpect(status().isCreated());
        return bearerToken(username);
    }

    private JsonNode search(String bearer, String query) throws Exception {
        var result = mockMvc.perform(get("/api/users/search").param("q", query).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean containsUsername(JsonNode results, String username) {
        for (JsonNode result : results) {
            if (result.get("username").asText().equals(username)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void searchMatchesByUsernameOrNicknameCaseInsensitively() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String targetUsername = "searchTarget" + suffix;
        String bearerSearcher = signupAndGetToken("searcher" + suffix, "Searcher");
        signupAndGetToken(targetUsername, "UniqueNickname" + suffix);

        JsonNode byUsername = search(bearerSearcher, targetUsername.substring(0, 10).toUpperCase());
        assertThat(containsUsername(byUsername, targetUsername)).isTrue();

        JsonNode byNickname = search(bearerSearcher, ("uniquenickname" + suffix));
        assertThat(containsUsername(byNickname, targetUsername)).isTrue();
    }

    @Test
    void searchExcludesTheRequesterThemselves() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String selfUsername = "selfSearch" + suffix;
        String bearerSelf = signupAndGetToken(selfUsername, "SelfNickname" + suffix);

        JsonNode results = search(bearerSelf, selfUsername);
        assertThat(containsUsername(results, selfUsername)).isFalse();
    }

    @Test
    void searchWithNoMatchesReturnsEmptyList() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String bearer = signupAndGetToken("noMatchUser" + suffix, "NoMatch" + suffix);

        JsonNode results = search(bearer, "no-such-user-" + UUID.randomUUID());
        assertThat(results).isEmpty();
    }
}
