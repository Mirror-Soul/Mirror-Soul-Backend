package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileRefreshRequestService;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.onboarding.OnboardingReqDTO;
import com.mirrorsoul.mirrorsoul_api.event.UserEmbeddingRefreshRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import com.mirrorsoul.mirrorsoul_api.repository.RegionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MbtiProfileRepository mbtiProfileRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CloneProfileRefreshRequestService cloneProfileRefreshRequestService;

    @Mock
    private FileService fileService;

    private OnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        onboardingService = new OnboardingService(
                userRepository,
                mbtiProfileRepository,
                regionRepository,
                eventPublisher,
                cloneProfileRefreshRequestService,
                fileService
        );
    }

    @Test
    void postProfileStoresProfileImageWhenObjectKeyProvided() {
        UUID userUuid = UUID.randomUUID();
        User user = User.builder()
                .uuid(userUuid)
                .email("user@example.com")
                .passwordHash("hash")
                .status(UserStatus.ONBOARD_A)
                .build();
        Region region = mock(Region.class);
        OnboardingReqDTO.personaReqDTO request = new OnboardingReqDTO.personaReqDTO();
        request.setNickname(" mirror ");
        request.setSidoName("서울특별시");
        request.setSigunguName("강남구");
        request.setEupmyeondongName("역삼동");
        request.setJobDescription("백엔드 개발자");
        request.setJobCertificationObjectKey("job-certifications/cert.pdf");
        request.setProfileImageObjectKey("profile-images/" + userUuid + "/avatar.png");

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(regionRepository.findBySidoNameAndSigunguNameAndEupmyeondongName("서울특별시", "강남구", "역삼동"))
                .thenReturn(region);
        when(fileService.verifyProfileImageAndBuildFileUrl(userUuid, request.getProfileImageObjectKey()))
                .thenReturn(new FileService.VerifiedS3Object(
                        "https://cdn.example.com/profile-images/" + userUuid + "/avatar.png",
                        request.getProfileImageObjectKey()
                ));

        onboardingService.postProfile(request, userUuid, Job.IT_TECH);

        assertThat(user.getName()).isEqualTo("mirror");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ONBOARD_B);
        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn.example.com/profile-images/" + userUuid + "/avatar.png");
        assertThat(user.getProfileImageObjectKey()).isEqualTo(request.getProfileImageObjectKey());
        verify(fileService).verifyProfileImageAndBuildFileUrl(userUuid, request.getProfileImageObjectKey());
        verify(userRepository).save(user);
        verify(eventPublisher).publishEvent(any(UserEmbeddingRefreshRequestedEvent.class));
    }
}
