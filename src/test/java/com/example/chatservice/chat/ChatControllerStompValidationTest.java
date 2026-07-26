package com.example.chatservice.chat;

import com.example.chatservice.auth.dto.SignupRequest;
import com.example.chatservice.chat.dto.ChatMessageRequest;
import com.example.chatservice.room.dto.CreateRoomRequest;
import com.example.chatservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check for the @Valid gap on ChatController#send: ChatMessageRequest always
 * carried @NotBlank/@Size constraints, but the handler never declared @Valid on the
 * @Payload parameter, so STOMP clients could push blank or oversized content straight
 * past them. Connects a real STOMP client over the app's websocket endpoint to prove
 * invalid payloads are rejected before they're persisted or fanned out, and that valid
 * ones still go through.
 */
class ChatControllerStompValidationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;
    private Long roomId;
    private String bearerA;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String alice = "stompAlice" + suffix;
        String bob = "stompBob" + suffix;
        signup(alice, "password1", "StompAlice");
        signup(bob, "password1", "StompBob");
        bearerA = bearerToken(alice);

        var createResult = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", bearerA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRoomRequest("stomp-room", List.of(bob)))))
                .andExpect(status().isCreated())
                .andReturn();
        roomId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.afterPropertiesSet();
        stompClient.setTaskScheduler(scheduler);
    }

    private void signup(String username, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(username, password, nickname))))
                .andExpect(status().isCreated());
    }

    private StompSession connect() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", bearerA);
        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws/websocket",
                        (org.springframework.web.socket.WebSocketHttpHeaders) null,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<Object> subscribeToRoom(StompSession session) {
        BlockingQueue<Object> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/rooms/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(payload);
            }
        });
        return received;
    }

    private int messageCountInRoom() throws Exception {
        var result = mockMvc.perform(get("/api/rooms/{roomId}/messages", roomId)
                        .header("Authorization", bearerA))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("totalElements").asInt();
    }

    @Test
    void blankContentIsRejectedAndNeverBroadcastOrPersisted() throws Exception {
        StompSession session = connect();
        BlockingQueue<Object> received = subscribeToRoom(session);

        session.send("/app/rooms/" + roomId + "/send", new ChatMessageRequest("   "));

        assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(messageCountInRoom()).isZero();

        session.disconnect();
    }

    @Test
    void oversizedContentIsRejectedAndNeverBroadcastOrPersisted() throws Exception {
        StompSession session = connect();
        BlockingQueue<Object> received = subscribeToRoom(session);

        session.send("/app/rooms/" + roomId + "/send", new ChatMessageRequest("x".repeat(2001)));

        assertThat(received.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(messageCountInRoom()).isZero();

        session.disconnect();
    }

    @Test
    void validContentIsBroadcastAndPersisted() throws Exception {
        StompSession session = connect();
        BlockingQueue<Object> received = subscribeToRoom(session);

        session.send("/app/rooms/" + roomId + "/send", new ChatMessageRequest("hello from stomp"));

        assertThat(received.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(messageCountInRoom()).isEqualTo(1);

        session.disconnect();
    }
}
