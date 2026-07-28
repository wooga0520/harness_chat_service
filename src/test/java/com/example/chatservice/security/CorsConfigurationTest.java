package com.example.chatservice.security;

import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms the React dev server origin (chat_service_front, run separately from this repo)
 * can call the API cross-origin, and that CORS isn't accidentally left wide open.
 */
class CorsConfigurationTest extends AbstractIntegrationTest {

    @Test
    void preflightFromTheViteDevOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/rooms")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                // sockjs-client marks cross-origin XHR transports (e.g. /ws/info polling) as
                // credentialed, so the browser rejects the response without this header even
                // though JWT auth itself doesn't rely on cookies.
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflightFromAnUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/rooms")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
