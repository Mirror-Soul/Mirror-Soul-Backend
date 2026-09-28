package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({CloneSimilarityService.class, CloneReadinessService.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CloneSimilarityPersistenceTest {
    @Autowired CloneSimilarityService service;
    @Autowired CloneRepository clones;
    @Autowired UserRepository users;
    @Autowired VoiceTrainingJobRepository voiceJobs;
    @Autowired AiVoiceProfileRepository voices;
    @Autowired FaceTrainingJobRepository faceJobs;
    @Autowired AiFaceProfileRepository faces;
    @Autowired RagProfileJobRepository ragJobs;
    @Autowired PlatformTransactionManager transactions;
    private TransactionTemplate tx;
    private Long cloneId;
    private Long voiceJobId;
    private Long userId;

    @BeforeEach
    void setup() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            UUID uuid = UUID.randomUUID();
            User user = users.saveAndFlush(User.builder().uuid(uuid).email(uuid + "@example.com")
                    .passwordHash("hash").status(UserStatus.ACTIVE).build());
            userId = user.getId();
            Clone clone = clones.saveAndFlush(Clone.builder().user(user).syncRate(new BigDecimal("82.0")).build());
            cloneId = clone.getId();
            var faceJob = faceJobs.saveAndFlush(FaceTrainingJob.create(user, FaceTrainingJobSource.ONBOARDING_FACE));
            faces.saveAndFlush(AiFaceProfile.ready(clone, faceJob, "bucket", "profile", "portrait", "manifest", null));
            clone.updateFaceSimilarity(faceJob.getId(), new BigDecimal("100"));
            var voiceJob = voiceJobs.saveAndFlush(VoiceTrainingJob.create(user));
            voiceJobId = voiceJob.getId();
            voices.saveAndFlush(AiVoiceProfile.create(clone, voiceJob, "voice", "ACTIVE", true));
            var rag = RagProfileJob.create(cloneId, LocalDateTime.now());
            rag.request(LocalDateTime.now());
            ragJobs.saveAndFlush(rag);
        });
    }

    private ClonePersonalityCompleteRequest profile(Long revision, String score) {
        return new ClonePersonalityCompleteRequest(CloneSimilarityCalculator.VERSION,
                new BigDecimal(score), new BigDecimal("100"), BigDecimal.ZERO, revision);
    }

    @Test
    void concurrentComponentsKeepBothUpdatesAndSwitchLegacyVersionAtomically() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var voice = executor.submit(() -> { start.await();
                service.completeVoice(cloneId, new CloneVoiceCompleteRequest(voiceJobId, new BigDecimal("100"))); return null; });
            var personality = executor.submit(() -> { start.await();
                service.completePersonality(cloneId, profile(1L, "100")); return null; });
            start.countDown();
            voice.get(15, TimeUnit.SECONDS);
            personality.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        Clone clone = clones.findById(cloneId).orElseThrow();
        assertThat(clone.getSyncRate()).isEqualTo(new BigDecimal("95.0"));
        assertThat(clone.getSimilarityScoreVersion()).isEqualTo(CloneSimilarityCalculator.VERSION);
        assertThat(clone.getVoiceSimilarityScore()).isEqualTo(new BigDecimal("100.00"));
        assertThat(clone.getProfileSimilarityScore()).isEqualTo(new BigDecimal("100.00"));
        assertThat(clone.getStatus()).isEqualTo("READY");
    }

    @Test
    void stalePendingRevisionAndDuplicatesCannotOverwriteLatestProfile() {
        tx.executeWithoutResult(status -> ragJobs.findLockedById(cloneId).orElseThrow().request(LocalDateTime.now()));
        service.completePersonality(cloneId, profile(1L, "10"));
        assertThat(clones.findById(cloneId).orElseThrow().isPersonalityTrainingCompleted()).isFalse();
        service.completePersonality(cloneId, profile(2L, "90"));
        service.completePersonality(cloneId, profile(2L, "10"));
        service.completePersonality(cloneId, profile(null, "10"));
        assertThat(clones.findById(cloneId).orElseThrow().getProfileSimilarityScore()).isEqualByComparingTo("90");
        assertThatThrownBy(() -> service.completePersonality(cloneId, profile(3L, "100"))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rollbackDoesNotConsumeSourceAndRetryCanCommitDecimalScore() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.completeVoice(cloneId, new CloneVoiceCompleteRequest(voiceJobId, new BigDecimal("82.35")));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(clones.findById(cloneId).orElseThrow().getSimilarityVoiceJobId()).isNull();
        service.completeVoice(cloneId, new CloneVoiceCompleteRequest(voiceJobId, new BigDecimal("82.35")));
        service.completeVoice(cloneId, new CloneVoiceCompleteRequest(voiceJobId, BigDecimal.ZERO));
        assertThat(clones.findById(cloneId).orElseThrow().getVoiceSimilarityScore()).isEqualByComparingTo("82.35");
        assertThat(clones.findById(cloneId).orElseThrow().getSyncRate()).isEqualByComparingTo("82.0");
    }

    @Test
    void voiceCallbackRejectsWrongOwnerAndUncommittedProfileAndIgnoresOlderActiveJob() {
        Long newerJobId = tx.execute(status -> {
            var newerJob = voiceJobs.saveAndFlush(VoiceTrainingJob.create(users.findById(userId).orElseThrow()));
            return newerJob.getId();
        });
        assertThatThrownBy(() -> service.completeVoice(cloneId, new CloneVoiceCompleteRequest(newerJobId, new BigDecimal("100"))))
                .isInstanceOf(RuntimeException.class);
        tx.executeWithoutResult(status -> voices.saveAndFlush(AiVoiceProfile.create(
                clones.findById(cloneId).orElseThrow(), voiceJobs.findById(newerJobId).orElseThrow(), "newer", "ACTIVE", true)));
        service.completeVoice(cloneId, new CloneVoiceCompleteRequest(voiceJobId, BigDecimal.ZERO));
        assertThat(clones.findById(cloneId).orElseThrow().getVoiceSimilarityScore()).isNull();
        service.completeVoice(cloneId, new CloneVoiceCompleteRequest(newerJobId, new BigDecimal("90")));
        assertThat(clones.findById(cloneId).orElseThrow().getVoiceSimilarityScore()).isEqualByComparingTo("90");
        Long foreignJob = tx.execute(status -> {
            UUID uuid = UUID.randomUUID();
            var user = users.saveAndFlush(User.builder().uuid(uuid).email(uuid + "@example.com")
                    .passwordHash("hash").status(UserStatus.ACTIVE).build());
            return voiceJobs.saveAndFlush(VoiceTrainingJob.create(user)).getId();
        });
        assertThatThrownBy(() -> service.completeVoice(cloneId, new CloneVoiceCompleteRequest(foreignJob, BigDecimal.ZERO)))
                .isInstanceOf(RuntimeException.class);
    }
}
