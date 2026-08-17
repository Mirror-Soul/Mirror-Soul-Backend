package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.match.MeetingReqDTO;
import com.mirrorsoul.mirrorsoul_api.dto.match.MeetingResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {
    private final MeetingRequestRepository meetingRequestRepository;
    private final CallMatchAnalysisRepository callMatchAnalysisRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final VideoCallRepository videoCallRepository;
    private final UserBlockRepository userBlockRepository;

    public MeetingResDTO.RequestListDTO getReceivedRequests(UUID currentUserUuid) {
        List<MeetingRequest> requests = meetingRequestRepository.findAllReceivedByStatus(
                currentUserUuid, MeetingRequestStatus.PENDING);

        Map<Long, CallMatchAnalysis> analysesByCallId = requests.isEmpty()
                ? Map.of()
                : callMatchAnalysisRepository
                        .findAllByVideoCallIdIn(requests.stream().map(r -> r.getVideoCall().getId()).toList())
                        .stream()
                        .filter(analysis -> analysis.getStatus() == CallMatchAnalysisStatus.COMPLETED)
                        .collect(Collectors.toMap(a -> a.getVideoCall().getId(), Function.identity()));

        List<MeetingResDTO.RequestDTO> result = requests.stream()
                .map(request -> toRequestDTO(request, analysesByCallId.get(request.getVideoCall().getId())))
                .toList();

        return MeetingResDTO.RequestListDTO.builder()
                .totalCount(result.size())
                .requests(result)
                .build();
    }

    @Transactional
    public MeetingResDTO.CreatedDTO createRequest(UUID currentUserUuid, MeetingReqDTO.CreateDTO request) {
        User sender = userRepository.findByUuid(currentUserUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        User receiver = userRepository.findByUuid(request.receiverUserUuid())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        if (sender.getId().equals(receiver.getId())) {
            throw new GeneralException(GeneralErrorCode.MEETING_SELF_REQUEST);
        }
        if (receiver.getStatus() != UserStatus.ACTIVE) {
            throw new GeneralException(GeneralErrorCode.MEETING_RECEIVER_INACTIVE);
        }
        validateNotBlocked(sender, receiver);

        String pairKey = pairKey(sender.getId(), receiver.getId());
        if (chatRoomRepository.existsByParticipantPairKey(pairKey)) {
            throw new GeneralException(GeneralErrorCode.MEETING_CHAT_ALREADY_EXISTS);
        }
        if (meetingRequestRepository.existsByActivePairKey(pairKey)) {
            throw new GeneralException(GeneralErrorCode.MEETING_REQUEST_ALREADY_PENDING);
        }

        VideoCall call = videoCallRepository.findById(request.videoCallId())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));
        validateCall(call, sender, receiver);

        MeetingRequest meetingRequest = MeetingRequest.builder()
                .sender(sender)
                .receiver(receiver)
                .videoCall(call)
                .message(request.message().trim())
                .status(MeetingRequestStatus.PENDING)
                .activePairKey(pairKey)
                .build();

        try {
            meetingRequestRepository.saveAndFlush(meetingRequest);
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(GeneralErrorCode.MEETING_REQUEST_ALREADY_PENDING);
        }

        return MeetingResDTO.CreatedDTO.builder()
                .requestId(meetingRequest.getId())
                .status(meetingRequest.getStatus())
                .requestedAt(meetingRequest.getCreatedAt())
                .build();
    }

    @Transactional
    public MeetingResDTO.RespondedDTO rejectRequest(UUID currentUserUuid, Long requestId) {
        MeetingRequest request = getRequestForUpdate(requestId);
        validateReceiver(request, currentUserUuid);
        validatePending(request);
        request.reject();

        return MeetingResDTO.RespondedDTO.builder()
                .requestId(request.getId())
                .status(request.getStatus())
                .respondedAt(request.getRespondedAt())
                .build();
    }

    @Transactional
    public MeetingResDTO.AcceptedDTO acceptRequest(UUID currentUserUuid, Long requestId) {
        MeetingRequest request = getRequestForUpdate(requestId);
        validateReceiver(request, currentUserUuid);

        Optional<ChatRoom> existingForRequest = chatRoomRepository.findByCreatedFromMeetingRequestId(requestId);
        if (request.getStatus() == MeetingRequestStatus.ACCEPTED && existingForRequest.isPresent()) {
            return acceptedResponse(request, existingForRequest.get(), false);
        }
        validatePending(request);
        validateNotBlocked(request.getSender(), request.getReceiver());

        String pairKey = pairKey(request.getSender().getId(), request.getReceiver().getId());
        if (chatRoomRepository.existsByParticipantPairKey(pairKey)) {
            throw new GeneralException(GeneralErrorCode.MEETING_CHAT_ALREADY_EXISTS);
        }

        request.accept();
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                .participantPairKey(pairKey)
                .roomType(ChatRoomType.DIRECT)
                .createdFromMeetingRequest(request)
                .build());

        LocalDateTime joinedAt = LocalDateTime.now();
        chatRoomMemberRepository.saveAll(List.of(
                ChatRoomMember.builder().chatRoom(chatRoom).user(request.getSender()).joinedAt(joinedAt).build(),
                ChatRoomMember.builder().chatRoom(chatRoom).user(request.getReceiver()).joinedAt(joinedAt).build()
        ));

        return acceptedResponse(request, chatRoom, true);
    }

    private void validateCall(VideoCall call, User sender, User receiver) {
        boolean valid = call.getStatus() == VideoCallStatus.COMPLETED
                && call.getUser().getId().equals(sender.getId())
                && call.getClone().getUser().getId().equals(receiver.getId());
        if (!valid) {
            throw new GeneralException(GeneralErrorCode.MEETING_INVALID_CALL);
        }
    }

    private MeetingRequest getRequestForUpdate(Long requestId) {
        return meetingRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEETING_REQUEST_NOT_FOUND));
    }

    private void validateReceiver(MeetingRequest request, UUID currentUserUuid) {
        if (!request.getReceiver().getUuid().equals(currentUserUuid)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private void validatePending(MeetingRequest request) {
        if (request.getStatus() != MeetingRequestStatus.PENDING) {
            throw new GeneralException(GeneralErrorCode.MEETING_REQUEST_ALREADY_PROCESSED);
        }
    }

    private void validateNotBlocked(User first, User second) {
        if (userBlockRepository.existsBetween(first.getId(), second.getId())) {
            throw new GeneralException(GeneralErrorCode.MEETING_RECEIVER_INACTIVE);
        }
    }

    private MeetingResDTO.RequestDTO toRequestDTO(MeetingRequest request, CallMatchAnalysis analysis) {
        User sender = request.getSender();
        return MeetingResDTO.RequestDTO.builder()
                .requestId(request.getId())
                .senderUserUuid(sender.getUuid())
                .name(sender.getName())
                .age(calculateAge(sender.getBirthDate()))
                .profileImageUrl(sender.getProfileImageUrl())
                .lastActiveAt(sender.getLastActiveAt())
                .twinSimilarity(analysis == null ? null : analysis.getTwinSimilarity())
                .message(request.getMessage())
                .conversationSummary(analysis == null ? null : analysis.getConversationSummary())
                .summaryPoints(analysis == null || analysis.getSummaryPoints() == null
                        ? List.of() : analysis.getSummaryPoints())
                .requestedAt(request.getCreatedAt())
                .build();
    }

    private Integer calculateAge(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }

    private String pairKey(Long firstUserId, Long secondUserId) {
        return Math.min(firstUserId, secondUserId) + ":" + Math.max(firstUserId, secondUserId);
    }

    private MeetingResDTO.AcceptedDTO acceptedResponse(MeetingRequest request, ChatRoom room, boolean created) {
        return MeetingResDTO.AcceptedDTO.builder()
                .requestId(request.getId())
                .status(request.getStatus())
                .chatRoomId(room.getId())
                .chatRoomCreated(created)
                .respondedAt(request.getRespondedAt())
                .build();
    }
}
