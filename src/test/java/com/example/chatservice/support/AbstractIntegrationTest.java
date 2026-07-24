package com.example.chatservice.support;

import com.example.chatservice.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Base for controller/full-stack tests: real security filter chain, real (containerized)
 * Postgres + Redis, MockMvc against the whole app. Use {@link #bearerToken(String)} to
 * authenticate as an existing user without re-implementing JWT construction in every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest implements Containers {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String bearerToken(String username) {
        return "Bearer " + jwtTokenProvider.generateToken(username);
    }
}
