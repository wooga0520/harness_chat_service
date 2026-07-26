package com.example.chatservice.chat;

import com.example.chatservice.chat.dto.ChatMessageResponse;
import com.example.chatservice.domain.ChatMessage;
import com.example.chatservice.domain.ChatRoom;
import com.example.chatservice.domain.MessageType;
import com.example.chatservice.domain.RoomParticipant;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
    @Mock private PresenceRegistry presenceRegistry;
    @Mock private OnlinePresenceService onlinePresenceService;
    @Mock private PresencePublisher presencePublisher;
    @Mock private ReadReceiptPublisher readReceiptPublisher;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatService chatService;
    private User sender;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRoomRepository, roomParticipantRepository,
                chatMessageRepository, userRepository, chatMessagePublisher,
                presenceRegistry, onlinePresenceService, presencePublisher, readReceiptPublisher,
                messagingTemplate);

        sender = User.builder().username("alice").password("hash").nickname("Alice").build();
        ReflectionTestUtils.setField(sender, "id", 1L);

        room = ChatRoom.newGroupRoom("general");
        ReflectionTestUtils.setField(room, "id", 10L);
    }

    private ChatMessage textMessageFrom(User author, ChatRoom inRoom, String content) {
        ChatMessage message = ChatMessage.builder().room(inRoom).sender(author).type(MessageType.TEXT).content(content).build();
        ReflectionTestUtils.setField(message, "id", 100L);
        return message;
    }

    @Test
    void senderCanEditTheirOwnTextMessage() {
        ChatMessage message = textMessageFrom(sender, room, "original");
        when(chatMessageRepository.findById(100L)).thenReturn(Optional.of(message));

        chatService.editMessage(10L, 100L, "alice", "edited");

        assertThat(message.getContent()).isEqualTo("edited");
        assertThat(message.getEditedAt()).isNotNull();

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(chatMessagePublisher).publish(captor.capture());
        assertThat(captor.getValue().content()).isEqualTo("edited");
        assertThat(captor.getValue().editedAt()).isNotNull();
    }

    @Test
    void otherUserCannotEditSomeoneElsesMessage() {
        ChatMessage message = textMessageFrom(sender, room, "original");
        when(chatMessageRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.editMessage(10L, 100L, "bob", "hacked"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        assertThat(message.getContent()).isEqualTo("original");
        verifyNoInteractions(chatMessagePublisher);
    }

    @Test
    void deletedMessageCannotBeEditedAgain() {
        ChatMessage message = textMessageFrom(sender, room, "original");
        message.delete();
        when(chatMessageRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.editMessage(10L, 100L, "alice", "edited"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);
    }

    @Test
    void senderCanDeleteTheirOwnTextMessageAndContentIsMaskedInTheBroadcast() {
        ChatMessage message = textMessageFrom(sender, room, "secret");
        when(chatMessageRepository.findById(100L)).thenReturn(Optional.of(message));

        chatService.deleteMessage(10L, 100L, "alice");

        assertThat(message.isDeleted()).isTrue();

        ArgumentCaptor<ChatMessageResponse> captor = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(chatMessagePublisher).publish(captor.capture());
        assertThat(captor.getValue().deleted()).isTrue();
        assertThat(captor.getValue().content()).isNull();
    }

    @Test
    void otherUserCannotDeleteSomeoneElsesMessage() {
        ChatMessage message = textMessageFrom(sender, room, "secret");
        when(chatMessageRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.deleteMessage(10L, 100L, "bob"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        assertThat(message.isDeleted()).isFalse();
    }

    @Test
    void editRejectsAMessageFromAnotherRoom() {
        ChatMessage message = textMessageFrom(sender, room, "original");
        when(chatMessageRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.editMessage(999L, 100L, "alice", "edited"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
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

    @Test
    void marksRoomReadForAParticipant() {
        RoomParticipant participant = RoomParticipant.builder().room(room).user(sender).build();
        LocalDateTime before = LocalDateTime.now().minusDays(1);
        ReflectionTestUtils.setField(participant, "lastReadAt", before);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(roomParticipantRepository.findByRoomIdAndUserId(10L, 1L)).thenReturn(Optional.of(participant));

        chatService.markRoomRead(10L, "alice");

        assertThat(participant.getLastReadAt()).isAfter(before);
    }

    @Test
    void markRoomReadRejectsUnknownSenderWith401() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.markRoomRead(10L, "ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void markRoomReadRejectsNonParticipantWith403() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(roomParticipantRepository.findByRoomIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.markRoomRead(10L, "alice"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }
}
