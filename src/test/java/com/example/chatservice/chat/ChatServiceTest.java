package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.domain.User;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomParticipantRepository;
import com.example.chatservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RoomParticipantRepository roomParticipantRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatMessagePublisher chatMessagePublisher;

    private ChatService chatService;
    private User sender;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomRepository, roomParticipantRepository,
                chatMessageRepository, userRepository, chatMessagePublisher);

        sender = User.builder().username("alice").password("hash").nickname("Alice").build();
        ReflectionTestUtils.setField(sender, "id", 1L);

        room = ChatRoom.newGroupRoom("general");
        ReflectionTestUtils.setField(room, "id", 10L);
    }

    @Test
    void publishesMessageWhenSenderIsARoomParticipant() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(roomParticipantRepository.existsByRoomIdAndUserId(10L, 1L)).thenReturn(true);
        when(chatMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        chatService.sendMessage(10L, "alice", MessageType.TEXT, "hello");

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(chatMessagePublisher).publish(captor.capture());

        ChatMessageResponse published = captor.getValue();
        assertThat(published.roomId()).isEqualTo(10L);
        assertThat(published.senderId()).isEqualTo(1L);
        assertThat(published.senderUsername()).isEqualTo("alice");
        assertThat(published.content()).isEqualTo("hello");
        assertThat(published.type()).isEqualTo(MessageType.TEXT);
    }

    @Test
    void rejectsUnknownSenderWith401() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(10L, "ghost", MessageType.TEXT, "hi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(chatMessagePublisher);
    }

    @Test
    void rejectsMissingRoomWith404() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(10L, "alice", MessageType.TEXT, "hi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verifyNoInteractions(chatMessagePublisher);
    }

    @Test
    void rejectsNonParticipantWith403() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(roomParticipantRepository.existsByRoomIdAndUserId(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> chatService.sendMessage(10L, "alice", MessageType.TEXT, "hi"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verifyNoInteractions(chatMessagePublisher);
    }
}
