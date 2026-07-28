package com.mirrorsoul.mirrorsoul_api.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.mirrorsoul.mirrorsoul_api.domain.PushDevice;
import com.mirrorsoul.mirrorsoul_api.event.ChatPushRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.ChatRoomMemberRepository;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@ConditionalOnProperty(name = "push.firebase.enabled", havingValue = "true")
public class PushNotificationService {

    private static final int MAX_MULTICAST_SIZE = 500;
    private static final String CHAT_MESSAGE_TYPE = "CHAT_MESSAGE";

    private final FirebaseMessaging firebaseMessaging;
    private final PushDeviceRepository pushDeviceRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final String androidChannelId;

    public PushNotificationService(
            FirebaseMessaging firebaseMessaging,
            PushDeviceRepository pushDeviceRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            @org.springframework.beans.factory.annotation.Value(
                    "${push.firebase.android-channel-id:chat_messages}")
            String androidChannelId) {
        this.firebaseMessaging = firebaseMessaging;
        this.pushDeviceRepository = pushDeviceRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.androidChannelId = androidChannelId;
    }

    @Transactional
    public void sendChatMessage(ChatPushRequestedEvent event) {
        List<UUID> recipientUuids = chatRoomMemberRepository.findPushRecipientUuids(
                event.chatRoomId(), event.senderUserUuid());
        if (recipientUuids.isEmpty()) {
            return;
        }

        List<PushDevice> devices =
                pushDeviceRepository.findAllByUserUuidInAndEnabledTrue(recipientUuids);
        for (int start = 0; start < devices.size(); start += MAX_MULTICAST_SIZE) {
            int end = Math.min(start + MAX_MULTICAST_SIZE, devices.size());
            sendBatch(event, devices.subList(start, end));
        }
    }

    private void sendBatch(ChatPushRequestedEvent event, List<PushDevice> devices) {
        if (devices.isEmpty()) {
            return;
        }

        List<String> tokens = devices.stream().map(PushDevice::getPushToken).toList();
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(resolveSenderName(event.senderName()))
                        .setBody("새 메시지가 도착했습니다.")
                        .build())
                .putData("type", CHAT_MESSAGE_TYPE)
                .putData("chatRoomId", event.chatRoomId().toString())
                .putData("messageId", event.messageId().toString())
                .putData("senderUserUuid", event.senderUserUuid().toString())
                .putData("route", "/chat/" + event.chatRoomId())
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(androidChannelId)
                                .setSound("default")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .addAllTokens(tokens)
                .build();

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            disableUnregisteredDevices(devices, response.getResponses());
            log.info(
                    "Chat push sent. roomId={}, messageId={}, success={}, failure={}",
                    event.chatRoomId(),
                    event.messageId(),
                    response.getSuccessCount(),
                    response.getFailureCount()
            );
        } catch (FirebaseMessagingException exception) {
            throw new IllegalStateException("Firebase multicast send failed", exception);
        }
    }

    private void disableUnregisteredDevices(
            List<PushDevice> devices, List<SendResponse> responses) {
        List<Long> disabledDeviceIds = new ArrayList<>();
        for (int index = 0; index < responses.size(); index++) {
            SendResponse response = responses.get(index);
            if (response.isSuccessful() || response.getException() == null) {
                continue;
            }
            if (response.getException().getMessagingErrorCode()
                    == MessagingErrorCode.UNREGISTERED) {
                PushDevice device = devices.get(index);
                device.disable();
                disabledDeviceIds.add(device.getId());
            }
        }
        if (!disabledDeviceIds.isEmpty()) {
            log.info("Disabled unregistered push devices. deviceIds={}", disabledDeviceIds);
        }
    }

    private String resolveSenderName(String senderName) {
        return senderName == null || senderName.isBlank() ? "MirrorSoul" : senderName.trim();
    }
}
