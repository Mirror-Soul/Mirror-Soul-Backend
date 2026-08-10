package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.VideoCallStatus;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationDetailServiceTest {

    private UserRepository userRepository;
    private CloneRepository cloneRepository;
    private AiVoiceProfileRepository aiVoiceProfileRepository;
    private VideoCallRepository videoCallRepository;
    private FileService fileService;
    private RecommendationDetailService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        cloneRepository = mock(CloneRepository.class);
        aiVoiceProfileRepository = mock(AiVoiceProfileRepository.class);
        videoCallRepository = mock(VideoCallRepository.class);
        fileService = mock(FileService.class);
        service = new RecommendationDetailService(
                userRepository,
                cloneRepository,
                aiVoiceProfileRepository,
                videoCallRepository,
                fileService
        );
    }

    @Test
    void getDetailReturnsProfileCloneAndVoicePreview() {
        UUID targetUuid = UUID.randomUUID();
        User target = mock(User.class);
        Clone clone = mock(Clone.class);
        Region region = mock(Region.class);
        AiVoiceProfile voiceProfile = mock(AiVoiceProfile.class);

        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
        when(target.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(target.getMatchingEnabled()).thenReturn(true);
        when(target.getUuid()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("서연");
        when(target.getBirthDate()).thenReturn(LocalDate.now().minusYears(28));
        when(target.getProfileImageUrl()).thenReturn("https://example.com/profile.jpg");
        when(target.getResidenceRegion()).thenReturn(region);
        when(target.getSelfIntroduction()).thenReturn("책과 음악을 좋아합니다.");
        when(region.getSidoName()).thenReturn("서울특별시");
        when(region.getSigunguName()).thenReturn("강남구");

        when(cloneRepository.findByUserUuid(targetUuid)).thenReturn(Optional.of(clone));
        when(clone.getId()).thenReturn(10L);
        when(clone.getSyncRate()).thenReturn(94);
        when(videoCallRepository.existsByCloneIdAndStatusIn(any(), any()))
                .thenReturn(true);

        when(aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(voiceProfile));
        when(voiceProfile.getIntroAudioBucket()).thenReturn("voice-bucket");
        when(voiceProfile.getIntroAudioObjectKey()).thenReturn("intro/voice.mp3");
        when(voiceProfile.getIntroAudioContentType()).thenReturn("audio/mpeg");
        when(voiceProfile.getIntroAudioDurationMs()).thenReturn(18_000);
        when(fileService.createPresignedDownloadUrl("voice-bucket", "intro/voice.mp3"))
                .thenReturn("https://example.com/signed-voice.mp3");

        HomeResDTO.RecommendationDetailDTO result = service.getDetail(targetUuid);

        assertThat(result.name()).isEqualTo("서연");
        assertThat(result.age()).isEqualTo(28);
        assertThat(result.syncRate()).isEqualTo(94);
        assertThat(result.region().sidoName()).isEqualTo("서울특별시");
        assertThat(result.region().sigunguName()).isEqualTo("강남구");
        assertThat(result.selfIntroduction()).isEqualTo("책과 음악을 좋아합니다.");
        assertThat(result.twinStatus()).isEqualTo("IN_CALL");
        assertThat(result.voicePreview().audioUrl())
                .isEqualTo("https://example.com/signed-voice.mp3");
        assertThat(result.voicePreview().durationMs()).isEqualTo(18_000);
    }

    @Test
    void getDetailRejectsUnavailableUser() {
        UUID targetUuid = UUID.randomUUID();
        User target = mock(User.class);
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
        when(target.getStatus()).thenReturn(UserStatus.INACTIVE);

        assertThatThrownBy(() -> service.getDetail(targetUuid))
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.RECOMMENDATION_TARGET_NOT_FOUND)
                );
    }
}
