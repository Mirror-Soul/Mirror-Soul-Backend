package com.mirrorsoul.mirrorsoul_api.service;

import com.mirrorsoul.mirrorsoul_api.domain.RagProfileJob;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.dto.RagProfileRequest;
import com.mirrorsoul.mirrorsoul_api.event.UserEmbeddingRequestedEvent;
import com.mirrorsoul.mirrorsoul_api.recommendation.EmbeddingType;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.*;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RagProfileJobService {
    private final CloneRepository clones;
    private final RagProfileJobRepository jobs;
    private final MbtiProfileRepository mbtiProfiles;
    private final InterviewRecordRepository interviews;

    // Synchronous listener joins the saving transaction: rollback also removes the request.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void request(UserEmbeddingRequestedEvent event) {
        if (event.type() != EmbeddingType.PROFILE && event.type() != EmbeddingType.INTERVIEW) return;
        var existing = clones.findByUserUuid(event.userUuid());
        if (existing.isEmpty()) throw new IllegalStateException("RAG profile requires an existing clone");
        var clone = clones.findLockedById(existing.get().getId()).orElseThrow();
        var status = clone.getUser().getStatus();
        if (status != UserStatus.ONBOARD_D && status != UserStatus.ACTIVE) return;
        LocalDateTime now = LocalDateTime.now();
        var job = jobs.findLockedById(clone.getId())
                .orElseGet(() -> jobs.save(RagProfileJob.create(clone.getId(), now)));
        job.request(now);
    }

    @Transactional
    public ClaimedProfile claim(Long cloneId) {
        var job = jobs.findLockedById(cloneId).orElseThrow();
        var now = LocalDateTime.now();
        if (!job.canClaim(now)) return null;
        var clone = clones.findById(cloneId).orElseThrow();
        var user = clone.getUser();
        String token = job.claim(now);
        if (user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.ONBOARD_D) {
            job.finish(token, job.getRequestedRevision(), null, now);
            return null; // Never send withdrawn/deleted members' profile data.
        }
        var mbti = mbtiProfiles.findByUser_Id(user.getId())
                .map(p -> p.getMbti().name()).orElse(null);
        var samples = interviews.findAllByUser_IdOrderByInterview_IdAsc(user.getId()).stream()
                .filter(r -> r.getAnswerText() != null && !r.getAnswerText().isBlank())
                .map(r -> new RagProfileRequest.InterviewSample(r.getInterview().getId(),
                        "", r.getInterview().getQuestion(), r.getAnswerText())).toList();
        var request = new RagProfileRequest(user.getUuid(), cloneId, "clone-" + cloneId,
                user.getBirthDate() == null ? null : Period.between(user.getBirthDate(), LocalDate.now()).getYears(),
                user.getGender() == null ? null : user.getGender().name().toLowerCase(Locale.ROOT),
                mbti, user.getSelfIntroduction(), List.of(), List.of(), samples, 12);
        return new ClaimedProfile(cloneId, job.getRequestedRevision(), token, request);
    }

    @Transactional
    public void finish(ClaimedProfile claim, String error) {
        jobs.findLockedById(claim.cloneId()).orElseThrow()
                .finish(claim.token(), claim.revision(), error, LocalDateTime.now());
    }

    public record ClaimedProfile(Long cloneId, long revision, String token, RagProfileRequest request) {}
}
