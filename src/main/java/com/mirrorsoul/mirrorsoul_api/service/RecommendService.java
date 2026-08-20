package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.Region;
import com.mirrorsoul.mirrorsoul_api.domain.Sigungu;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.UserPreferredSigungu;
import com.mirrorsoul.mirrorsoul_api.domain.ClonePersonalityTag;
import com.mirrorsoul.mirrorsoul_api.domain.MbtiProfile;
import com.mirrorsoul.mirrorsoul_api.domain.enums.MbtiType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.RecommendResDTO;
import com.mirrorsoul.mirrorsoul_api.recommendation.UserEmbeddingRepository;
import com.mirrorsoul.mirrorsoul_api.recommendation.VectorSimilarityScores;
import com.mirrorsoul.mirrorsoul_api.repository.UserPreferredSigunguRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.ClonePersonalityTagRepository;
import com.mirrorsoul.mirrorsoul_api.repository.MbtiProfileRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mirrorsoul.mirrorsoul_api.recommendation.RecommendationPolicy.SWIPE_REEXPOSURE_DAYS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendService {

    private static final int ADULT_AGE = 19;
    private static final int LONG_INACTIVE_DAYS = 30;
    private final UserRepository userRepository;
    private final UserPreferredSigunguRepository preferredSigunguRepository;
    private final RecommendationScoreCalculator scoreCalculator;
    private final ObjectProvider<UserEmbeddingRepository> embeddingRepositoryProvider;
    private final MbtiProfileRepository mbtiProfileRepository;
    private final ClonePersonalityTagRepository clonePersonalityTagRepository;

    public RecommendResDTO.RecommendationSliceDTO getRecommendations(
            UUID currentUserUuid,
            Pageable pageable
    ) {
        User requester = userRepository.findByUuid(currentUserUuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        if (requester.getStatus() != UserStatus.ACTIVE
                || requester.getBirthDate() == null
                || requester.getGender() == null) {
            throw new GeneralException(
                    GeneralErrorCode.FORBIDDEN,
                    "온보딩을 완료한 사용자만 추천 목록을 조회할 수 있습니다."
            );
        }

        LocalDate today = LocalDate.now();
        LocalDate adultBirthDateCutoff = today.minusYears(ADULT_AGE);
        boolean adult = !requester.getBirthDate().isAfter(adultBirthDateCutoff);
        LocalDateTime now = LocalDateTime.now();

        // 차단 도메인이 추가되면 이 후보 쿼리에 양방향 차단 NOT EXISTS 조건을 추가한다.
        List<User> candidates = userRepository.findRecommendationCandidates(
                requester.getId(),
                requester.getGender(),
                adult,
                adultBirthDateCutoff,
                now.minusDays(LONG_INACTIVE_DAYS),
                now.minusDays(SWIPE_REEXPOSURE_DAYS)
        );

        Map<UUID, VectorSimilarityScores> vectorsByUserUuid = loadVectorScores(
                requester.getUuid(),
                candidates
        );
        Map<Long, MbtiType> mbtiByUserId = loadMbtiByUserId(candidates);
        Map<Long, List<String>> hashtagsByUserId = loadHashtagsByUserId(candidates);

        Region requesterResidence = requester.getResidenceRegion();
        List<Sigungu> requesterPreferredSigungu = loadPreferredSigungu(requester);

        List<RecommendResDTO.RecommendationDTO> rankedCandidates = candidates.stream()
                .map(candidate -> {
                    int score = scoreCalculator.calculate(
                            requester.getBirthDate(),
                            candidate.getBirthDate(),
                            requesterResidence,
                            candidate.getResidenceRegion(),
                            requesterPreferredSigungu,
                            vectorsByUserUuid.get(candidate.getUuid())
                    );
                    return new RecommendResDTO.RecommendationDTO(
                            candidate.getUuid(),
                            candidate.getName(),
                            calculateAge(candidate.getBirthDate(), today),
                            candidate.getJob(),
                            hasSubmittedJobCertification(candidate),
                            toResidence(candidate.getResidenceRegion()),
                            candidate.getSelfIntroduction(),
                            mbtiByUserId.get(candidate.getId()),
                            hashtagsByUserId.getOrDefault(candidate.getId(), List.of()),
                            candidate.getProfileImageUrl(),
                            score
                    );
                })
                .sorted((first, second) -> Integer.compare(
                        second.recommendationScore(),
                        first.recommendationScore()
                ))
                .toList();

        int fromIndex = (int) Math.min(pageable.getOffset(), rankedCandidates.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), rankedCandidates.size());
        List<RecommendResDTO.RecommendationDTO> recommendations =
                rankedCandidates.subList(fromIndex, toIndex);

        return new RecommendResDTO.RecommendationSliceDTO(
                recommendations,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                toIndex < rankedCandidates.size()
        );
    }

    private List<Sigungu> loadPreferredSigungu(User requester) {
        return preferredSigunguRepository
                .findAllByUserIdOrderByCreatedAtAscIdAsc(requester.getId()).stream()
                .map(UserPreferredSigungu::getSigungu)
                .toList();
    }

    private Map<UUID, VectorSimilarityScores> loadVectorScores(
            UUID requesterUuid,
            List<User> candidates
    ) {
        UserEmbeddingRepository repository = embeddingRepositoryProvider.getIfAvailable();
        if (repository == null || candidates.isEmpty()) {
            return Map.of();
        }

        return repository.findSimilarityScores(
                        requesterUuid,
                        candidates.stream().map(User::getUuid).toList()
                ).stream()
                .collect(Collectors.toMap(
                        VectorSimilarityScores::userUuid,
                        Function.identity()
                ));
    }

    private Map<Long, MbtiType> loadMbtiByUserId(List<User> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        return mbtiProfileRepository.findAllByUser_IdIn(userIds(candidates)).stream()
                .collect(Collectors.toMap(
                        profile -> profile.getUser().getId(),
                        MbtiProfile::getMbti
                ));
    }

    private Map<Long, List<String>> loadHashtagsByUserId(List<User> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        return clonePersonalityTagRepository.findAllByUserIds(userIds(candidates)).stream()
                .collect(Collectors.groupingBy(
                        tag -> tag.getClone().getUser().getId(),
                        Collectors.mapping(ClonePersonalityTag::getContent, Collectors.toList())
                ));
    }

    private List<Long> userIds(List<User> candidates) {
        return candidates.stream().map(User::getId).toList();
    }

    private Integer calculateAge(LocalDate birthDate, LocalDate today) {
        return birthDate == null ? null : Period.between(birthDate, today).getYears();
    }

    private boolean hasSubmittedJobCertification(User user) {
        return user.getJobCertificationObjectKey() != null
                && !user.getJobCertificationObjectKey().isBlank();
    }

    private RecommendResDTO.ResidenceDTO toResidence(Region region) {
        if (region == null) {
            return null;
        }
        return new RecommendResDTO.ResidenceDTO(region.getSidoName(), region.getSigunguName());
    }

}
