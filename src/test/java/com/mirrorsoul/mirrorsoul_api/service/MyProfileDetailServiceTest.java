package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.AiVoiceProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.ClonePersonalityTag;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.dto.profile.ProfileResDTO;
import com.mirrorsoul.mirrorsoul_api.repository.AiVoiceProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyProfileDetailServiceTest {

    private UserRepository userRepository;
    private CloneRepository cloneRepository;
    private MbtiProfileRepository mbtiProfileRepository;
    private ClonePersonalityTagRepository clonePersonalityTagRepository;
    private AiVoiceProfileRepository aiVoiceProfileRepository;
    private FileService fileService;
    private MyProfileDetailService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        cloneRepository = mock(CloneRepository.class);
        mbtiProfileRepository = mock(MbtiProfileRepository.class);
        clonePersonalityTagRepository = mock(ClonePersonalityTagRepository.class);
        aiVoiceProfileRepository = mock(AiVoiceProfileRepository.class);
        fileService = mock(FileService.class);
        service = new MyProfileDetailService(
                userRepository,
                cloneRepository,
                mbtiProfileRepository,
                clonePersonalityTagRepository,
                aiVoiceProfileRepository,
                fileService
        );
    }

    @Test
    void getDetailReturnsMyCompleteProfile() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        Clone clone = mock(Clone.class);
        Region region = mock(Region.class);
        MbtiProfile mbtiProfile = mock(MbtiProfile.class);
        ClonePersonalityTag tag = mock(ClonePersonalityTag.class);
        AiVoiceProfile voiceProfile = mock(AiVoiceProfile.class);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getUuid()).thenReturn(userUuid);
        when(user.getEmail()).thenReturn("me@example.com");
        when(user.getName()).thenReturn("서연");
        when(user.getBirthDate()).thenReturn(LocalDate.now().minusYears(28));
        when(user.getProfileImageUrl()).thenReturn("https://example.com/profile.jpg");
        when(user.getResidenceRegion()).thenReturn(region);
        when(user.getJob()).thenReturn(Job.IT_TECH);
        when(user.getJobDescription()).thenReturn("백엔드 개발자");
        when(user.getJobCertificationObjectKey()).thenReturn("certification/job.png");
        when(user.getSelfIntroduction()).thenReturn("책과 음악을 좋아합니다.");
        when(user.getMatchingEnabled()).thenReturn(true);
        when(region.getSidoName()).thenReturn("서울특별시");
        when(region.getSigunguName()).thenReturn("강남구");

        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(clone));
        when(clone.getId()).thenReturn(10L);
        when(clone.getSyncRate()).thenReturn(94);
        when(mbtiProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(mbtiProfile));
        when(mbtiProfile.getMbti()).thenReturn(MbtiType.INFJ);
        when(mbtiProfile.getIeScore()).thenReturn(40);
        when(mbtiProfile.getNsScore()).thenReturn(70);
        when(mbtiProfile.getFtScore()).thenReturn(55);
        when(mbtiProfile.getPjScore()).thenReturn(60);
        when(tag.getContent()).thenReturn("사고가 깊은");
        when(clonePersonalityTagRepository.findAllByCloneIdOrderByDisplayOrderAsc(10L))
                .thenReturn(List.of(tag));

        when(aiVoiceProfileRepository
                .findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(10L))
                .thenReturn(Optional.of(voiceProfile));
        when(voiceProfile.getIntroAudioBucket()).thenReturn("voice-bucket");
        when(voiceProfile.getIntroAudioObjectKey()).thenReturn("intro/voice.mp3");
        when(voiceProfile.getIntroAudioContentType()).thenReturn("audio/mpeg");
        when(voiceProfile.getIntroAudioDurationMs()).thenReturn(18_000);
        when(fileService.createPresignedDownloadUrl("voice-bucket", "intro/voice.mp3"))
                .thenReturn("https://example.com/signed-voice.mp3");

        ProfileResDTO.MyProfileDetailDTO result = service.getDetail(userUuid);

        assertThat(result.userUuid()).isEqualTo(userUuid);
        assertThat(result.email()).isEqualTo("me@example.com");
        assertThat(result.name()).isEqualTo("서연");
        assertThat(result.age()).isEqualTo(28);
        assertThat(result.syncRate()).isEqualTo(94);
        assertThat(result.region().sigunguName()).isEqualTo("강남구");
        assertThat(result.job()).isEqualTo(Job.IT_TECH);
        assertThat(result.jobDescription()).isEqualTo("백엔드 개발자");
        assertThat(result.jobCertificationSubmitted()).isTrue();
        assertThat(result.mbti()).isEqualTo(MbtiType.INFJ);
        assertThat(result.mbtiAxisScores().nsScore()).isEqualTo(70);
        assertThat(result.personalityTags()).containsExactly("사고가 깊은");
        assertThat(result.voicePreview().audioUrl())
                .isEqualTo("https://example.com/signed-voice.mp3");
        assertThat(result.matchingEnabled()).isTrue();
    }

    @Test
    void getDetailReturnsPartialProfileWhenOnboardingDataIsMissing() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(1L);
        when(user.getUuid()).thenReturn(userUuid);
        when(cloneRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());
        when(mbtiProfileRepository.findByUser_Id(1L)).thenReturn(Optional.empty());

        ProfileResDTO.MyProfileDetailDTO result = service.getDetail(userUuid);

        assertThat(result.syncRate()).isNull();
        assertThat(result.region()).isNull();
        assertThat(result.mbti()).isNull();
        assertThat(result.mbtiAxisScores()).isNull();
        assertThat(result.personalityTags()).isEmpty();
        assertThat(result.voicePreview()).isNull();
        assertThat(result.jobCertificationSubmitted()).isFalse();
        assertThat(result.matchingEnabled()).isFalse();
        verify(clonePersonalityTagRepository, never())
                .findAllByCloneIdOrderByDisplayOrderAsc(org.mockito.ArgumentMatchers.anyLong());
        verify(aiVoiceProfileRepository, never())
                .findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(
                        org.mockito.ArgumentMatchers.anyLong()
                );
    }

    @Test
    void getDetailRejectsUnknownUser() {
        UUID userUuid = UUID.randomUUID();
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(userUuid))
                .isInstanceOfSatisfying(
                        GeneralException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(GeneralErrorCode.USER_NOT_FOUND)
                );
    }
}
