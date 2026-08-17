package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.ClonePersonalityTag;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.dto.home.HomeResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserBlockRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationDetailServiceTest {

    private UserRepository userRepository;
    private CloneRepository cloneRepository;
    private UserBlockRepository userBlockRepository;
    private MbtiProfileRepository mbtiProfileRepository;
    private ClonePersonalityTagRepository clonePersonalityTagRepository;
    private AiVoiceProfileRepository aiVoiceProfileRepository;
    private FileService fileService;
    private RecommendationDetailService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        cloneRepository = mock(CloneRepository.class);
        userBlockRepository = mock(UserBlockRepository.class);
        mbtiProfileRepository = mock(MbtiProfileRepository.class);
        clonePersonalityTagRepository = mock(ClonePersonalityTagRepository.class);
        aiVoiceProfileRepository = mock(AiVoiceProfileRepository.class);
        fileService = mock(FileService.class);
        service = new RecommendationDetailService(
                userRepository,
                userBlockRepository,
                cloneRepository,
                mbtiProfileRepository,
                clonePersonalityTagRepository,
                aiVoiceProfileRepository,
                fileService
        );
    }

    @Test
    void getDetailReturnsProfileCloneAndVoicePreview() {
        UUID targetUuid = UUID.randomUUID();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = mock(User.class);
        User target = mock(User.class);
        Clone clone = mock(Clone.class);
        Region region = mock(Region.class);
        AiVoiceProfile voiceProfile = mock(AiVoiceProfile.class);
        MbtiProfile mbtiProfile = mock(MbtiProfile.class);
        ClonePersonalityTag firstTag = mock(ClonePersonalityTag.class);
        ClonePersonalityTag secondTag = mock(ClonePersonalityTag.class);

        when(userRepository.findByUuid(currentUserUuid)).thenReturn(Optional.of(currentUser));
        when(currentUser.getId()).thenReturn(99L);
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
        when(target.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(target.getMatchingEnabled()).thenReturn(true);
        when(target.getUuid()).thenReturn(targetUuid);
        when(target.getId()).thenReturn(1L);
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
        when(mbtiProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(mbtiProfile));
        when(mbtiProfile.getMbti()).thenReturn(MbtiType.INFJ);
        when(firstTag.getContent()).thenReturn("사고가 깊은");
        when(secondTag.getContent()).thenReturn("차분한 말투");
        when(clonePersonalityTagRepository.findAllByCloneIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of(firstTag, secondTag));

        when(aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(voiceProfile));
        when(voiceProfile.getIntroAudioBucket()).thenReturn("voice-bucket");
        when(voiceProfile.getIntroAudioObjectKey()).thenReturn("intro/voice.mp3");
        when(voiceProfile.getIntroAudioContentType()).thenReturn("audio/mpeg");
        when(voiceProfile.getIntroAudioDurationMs()).thenReturn(18_000);
        when(fileService.createPresignedDownloadUrl("voice-bucket", "intro/voice.mp3"))
                .thenReturn("https://example.com/signed-voice.mp3");

        HomeResDTO.RecommendationDetailDTO result = service.getDetail(currentUserUuid, targetUuid);

        assertThat(result.name()).isEqualTo("서연");
        assertThat(result.age()).isEqualTo(28);
        assertThat(result.syncRate()).isEqualTo(94);
        assertThat(result.region().sidoName()).isEqualTo("서울특별시");
        assertThat(result.region().sigunguName()).isEqualTo("강남구");
        assertThat(result.selfIntroduction()).isEqualTo("책과 음악을 좋아합니다.");
        assertThat(result.mbti()).isEqualTo(MbtiType.INFJ);
        assertThat(result.hashtags()).containsExactly("사고가 깊은", "차분한 말투");
        assertThat(result.voicePreview().audioUrl())
                .isEqualTo("https://example.com/signed-voice.mp3");
        assertThat(result.voicePreview().durationMs()).isEqualTo(18_000);
    }

    @Test
    void getDetailRejectsUnavailableUser() {
        UUID targetUuid = UUID.randomUUID();
        UUID currentUserUuid = UUID.randomUUID();
        User currentUser = mock(User.class);
        User target = mock(User.class);
        when(userRepository.findByUuid(currentUserUuid)).thenReturn(Optional.of(currentUser));
        when(userRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
        when(target.getStatus()).thenReturn(UserStatus.INACTIVE);

        assertThatThrownBy(() -> service.getDetail(currentUserUuid, targetUuid))
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.RECOMMENDATION_TARGET_NOT_FOUND)
                );
    }
}
