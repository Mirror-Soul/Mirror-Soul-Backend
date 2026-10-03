package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.VoiceTrainingJob;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Gender;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.call.AiCallContextDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiCallContextServiceTest {

    private VideoCallRepository videoCallRepository;
    private MbtiProfileRepository mbtiProfileRepository;
    private AiVoiceProfileRepository aiVoiceProfileRepository;
    private AiCallContextService service;

    @BeforeEach
    void setUp() {
        videoCallRepository = mock(VideoCallRepository.class);
        mbtiProfileRepository = mock(MbtiProfileRepository.class);
        aiVoiceProfileRepository = mock(AiVoiceProfileRepository.class);
        service = new AiCallContextService(
                videoCallRepository,
                mbtiProfileRepository,
                aiVoiceProfileRepository
        );
    }

    @Test
    void returnsCanonicalCallContext() throws Exception {
        UUID ownerUuid = UUID.randomUUID();
        VideoCall call = mock(VideoCall.class);
        Clone clone = mock(Clone.class);
        User owner = mock(User.class);
        MbtiProfile mbtiProfile = mock(MbtiProfile.class);
        AiVoiceProfile voiceProfile = mock(AiVoiceProfile.class);
        VoiceTrainingJob voiceJob = mock(VoiceTrainingJob.class);

        when(videoCallRepository.findByIdWithParticipants(1L)).thenReturn(Optional.of(call));
        when(call.getId()).thenReturn(1L);
        when(call.getRoomId()).thenReturn("call-abc");
        when(call.getMediaType()).thenReturn(CallMediaType.VIDEO);
        when(call.getStatus()).thenReturn(VideoCallStatus.READY);
        when(call.getClone()).thenReturn(clone);
        when(clone.getId()).thenReturn(10L);
        when(clone.getStatus()).thenReturn("READY");
        when(clone.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(20L);
        when(owner.getUuid()).thenReturn(ownerUuid);
        when(owner.getName()).thenReturn("홍길동");
        when(owner.getGender()).thenReturn(Gender.MALE);
        when(owner.getBirthDate()).thenReturn(LocalDate.of(2002, 3, 10));
        when(owner.getJob()).thenReturn(Job.STUDENT);
        when(owner.getJobDescription()).thenReturn("컴퓨터공학과 학생");
        when(owner.getSelfIntroduction()).thenReturn("영화와 음악을 좋아합니다.");
        when(mbtiProfileRepository.findByUser_Id(20L)).thenReturn(Optional.of(mbtiProfile));
        when(mbtiProfile.getMbti()).thenReturn(MbtiType.INFP);
        when(aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueAndStatusOrderByCreatedAtDescIdDesc(10L, "ACTIVE"))
                .thenReturn(Optional.of(voiceProfile));
        when(voiceProfile.getId()).thenReturn(32L);
        when(voiceProfile.getVoiceTrainingJob()).thenReturn(voiceJob);
        when(voiceProfile.getElevenlabsVoiceId()).thenReturn("elevenlabs-voice-id");
        when(voiceJob.getId()).thenReturn(41L);

        AiCallContextDTO result = service.getContext(1L);

        assertThat(result.schemaVersion()).isEqualTo(1);
        assertThat(result.callId()).isEqualTo(1L);
        assertThat(result.roomId()).isEqualTo("call-abc");
        assertThat(result.mediaType()).isEqualTo(CallMediaType.VIDEO);
        assertThat(result.status()).isEqualTo(VideoCallStatus.READY);
        assertThat(result.cloneContext().cloneId()).isEqualTo(10L);
        assertThat(result.cloneContext().userUuid()).isEqualTo(ownerUuid);
        assertThat(result.cloneContext().persona().mbti()).isEqualTo(MbtiType.INFP);
        assertThat(result.cloneContext().voice().voiceProfileId()).isEqualTo(32L);
        assertThat(result.cloneContext().voice().voiceTrainingJobId()).isEqualTo(41L);
        assertThat(result.cloneContext().voice().provider()).isEqualTo("ELEVENLABS");
        assertThat(result.cloneContext().voice().voiceId()).isEqualTo("elevenlabs-voice-id");
        assertThat(new tools.jackson.databind.ObjectMapper().writeValueAsString(result))
                .contains("\"clone\"")
                .doesNotContain("cloneContext");
    }

    @Test
    void rejectsMissingCall() {
        when(videoCallRepository.findByIdWithParticipants(99L)).thenReturn(Optional.empty());

        assertErrorCode(99L, GeneralErrorCode.CALL_NOT_FOUND);
    }

    @Test
    void rejectsCompletedCall() {
        VideoCall call = mock(VideoCall.class);
        when(videoCallRepository.findByIdWithParticipants(1L)).thenReturn(Optional.of(call));
        when(call.getStatus()).thenReturn(VideoCallStatus.COMPLETED);

        assertErrorCode(1L, GeneralErrorCode.INVALID_CALL_STATUS);
    }

    @Test
    void rejectsCloneThatIsNotReady() {
        VideoCall call = mock(VideoCall.class);
        Clone clone = mock(Clone.class);
        when(videoCallRepository.findByIdWithParticipants(1L)).thenReturn(Optional.of(call));
        when(call.getStatus()).thenReturn(VideoCallStatus.READY);
        when(call.getClone()).thenReturn(clone);
        when(clone.getStatus()).thenReturn("PENDING");

        assertErrorCode(1L, GeneralErrorCode.CLONE_NOT_READY);
    }

    @Test
    void rejectsMissingActiveVoiceProfile() {
        VideoCall call = mock(VideoCall.class);
        Clone clone = mock(Clone.class);
        User owner = mock(User.class);
        when(videoCallRepository.findByIdWithParticipants(1L)).thenReturn(Optional.of(call));
        when(call.getStatus()).thenReturn(VideoCallStatus.READY);
        when(call.getClone()).thenReturn(clone);
        when(clone.getId()).thenReturn(10L);
        when(clone.getStatus()).thenReturn("READY");
        when(clone.getUser()).thenReturn(owner);
        when(owner.getId()).thenReturn(20L);
        when(mbtiProfileRepository.findByUser_Id(20L)).thenReturn(Optional.empty());
        when(aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueAndStatusOrderByCreatedAtDescIdDesc(10L, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertErrorCode(1L, GeneralErrorCode.VOICE_PROFILE_NOT_READY);
    }

    private void assertErrorCode(Long callId, GeneralErrorCode expectedCode) {
        assertThatThrownBy(() -> service.getContext(callId))
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedCode)
                );
    }
}
