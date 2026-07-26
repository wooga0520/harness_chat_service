package com.example.chatservice.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * Converts server-side STOMP failures (auth rejection from
 * StompAuthChannelInterceptor, validation errors from @Valid @Payload, etc.)
 * into an explicit STOMP ERROR frame the client can read, instead of the
 * connection just closing with no reason. Without this, room.js's connect()
 * failure callback has no way to tell "bad token" apart from "network
 * hiccup" and would otherwise retry forever with the same bad token.
 */
@Component
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String message = cause.getMessage() != null ? cause.getMessage() : "STOMP processing error";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(message);
        accessor.setLeaveMutable(true);

        return MessageBuilder.createMessage(message.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }
}
