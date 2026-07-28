package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.mirrorsoul.mirrorsoul_api.domain.PushDevice;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.PushDevicePlatform;
import com.mirrorsoul.mirrorsoul_api.event.ChatPushRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.ChatRoomMemberRepository;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushNotificationServiceTest {

    private FirebaseMessaging firebaseMessaging;
    private PushDeviceRepository pushDeviceRepository;
    private ChatRoomMemberRepository chatRoomMemberRepository;
    private PushNotificationService pushNotificationService;

    @BeforeEach
    void setUp() {
        firebaseMessaging = mock(FirebaseMessaging.class);
        pushDeviceRepository = mock(PushDeviceRepository.class);
        chatRoomMemberRepository = mock(ChatRoomMemberRepository.class);
        pushNotificationService = new PushNotificationService(
                firebaseMessaging,
                pushDeviceRepository,
                chatRoomMemberRepository,
                "chat_messages"
        );
    }

    @Test
    void sendsChatPushToEnabledRecipientDevices() throws Exception {
        UUID senderUuid = UUID.randomUUID();
        UUID receiverUuid = UUID.randomUUID();
        PushDevice device = device(10L, receiverUuid, "receiver-token");
        ChatPushRequestedEvent event =
                new ChatPushRequestedEvent(20L, 30L, senderUuid, "보낸 사람");
        BatchResponse response = mock(BatchResponse.class);

        when(chatRoomMemberRepository.findPushRecipientUuids(20L, senderUuid))
                .thenReturn(List.of(receiverUuid));
        when(pushDeviceRepository.findAllByUserUuidInAndEnabledTrue(List.of(receiverUuid)))
                .thenReturn(List.of(device));
        when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(response);
        when(response.getResponses()).thenReturn(List.of());

        pushNotificationService.sendChatMessage(event);

        verify(firebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
    }

    @Test
    void doesNotCallFirebaseWhenNoMemberHasNotificationsEnabled() throws Exception {
        UUID senderUuid = UUID.randomUUID();
        ChatPushRequestedEvent event =
                new ChatPushRequestedEvent(20L, 30L, senderUuid, "보낸 사람");
        when(chatRoomMemberRepository.findPushRecipientUuids(20L, senderUuid))
                .thenReturn(List.of());

        pushNotificationService.sendChatMessage(event);

        verify(pushDeviceRepository, never())
                .findAllByUserUuidInAndEnabledTrue(any());
        verify(firebaseMessaging, never()).sendEachForMulticast(any());
    }

    @Test
    void disablesDeviceWhenFirebaseReportsUnregisteredToken() throws Exception {
        UUID senderUuid = UUID.randomUUID();
        UUID receiverUuid = UUID.randomUUID();
        PushDevice device = device(10L, receiverUuid, "expired-token");
        ChatPushRequestedEvent event =
                new ChatPushRequestedEvent(20L, 30L, senderUuid, "보낸 사람");
        BatchResponse batchResponse = mock(BatchResponse.class);
        SendResponse sendResponse = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);

        when(chatRoomMemberRepository.findPushRecipientUuids(20L, senderUuid))
                .thenReturn(List.of(receiverUuid));
        when(pushDeviceRepository.findAllByUserUuidInAndEnabledTrue(List.of(receiverUuid)))
                .thenReturn(List.of(device));
        when(firebaseMessaging.sendEachForMulticast(any())).thenReturn(batchResponse);
        when(batchResponse.getResponses()).thenReturn(List.of(sendResponse));
        when(sendResponse.isSuccessful()).thenReturn(false);
        when(sendResponse.getException()).thenReturn(exception);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        pushNotificationService.sendChatMessage(event);

        assertThat(device.isEnabled()).isFalse();
    }

    private PushDevice device(Long id, UUID userUuid, String token) {
        User user = User.builder()
                .id(id)
                .uuid(userUuid)
                .email(userUuid + "@example.com")
                .passwordHash("password")
                .build();
        return PushDevice.builder()
                .id(id)
                .user(user)
                .installationId(UUID.randomUUID())
                .pushToken(token)
                .platform(PushDevicePlatform.IOS)
                .enabled(true)
                .lastSeenAt(LocalDateTime.now())
                .build();
    }
}
