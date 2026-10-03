package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;

import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.voice.VoiceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.dto.voice.VoiceTrainingResultDTO.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({VoiceTrainingResultService.class, CloneReadinessService.class, JpaAuditingConfig.class,
        VoiceTrainingResultPersistenceTest.StorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VoiceTrainingResultPersistenceTest {
    @TestConfiguration
    static class StorageConfig {
        @Bean AwsS3Properties storage() {
            var storage = new AwsS3Properties();
            storage.setBucket("test-bucket");
            return storage;
        }
    }

    @Autowired VoiceTrainingResultService service;
    @Autowired UserRepository users;
    @Autowired CloneRepository clones;
    @Autowired VoiceTrainingJobRepository jobs;
    @Autowired AiVoiceProfileRepository profiles;
    @Autowired PlatformTransactionManager transactions;
    private UUID uuid;
    private Long cloneId;
    private Long jobId;
    private Long userId;

    @BeforeEach
    void setup() {
        uuid = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User user = users.saveAndFlush(User.builder().uuid(uuid).email(uuid + "@example.com")
                    .passwordHash("hash").status(UserStatus.ACTIVE).build());
            userId = user.getId();
            cloneId = clones.saveAndFlush(Clone.builder().user(user).syncRate(BigDecimal.ZERO).build()).getId();
            jobId = jobs.saveAndFlush(VoiceTrainingJob.create(user)).getId();
        });
    }

    private VoiceTrainingResultDTO completed(Long id, String score) {
        return new VoiceTrainingResultDTO("VOICE_TRAINING_STATUS", id, uuid, null, "COMPLETED",
                new Result("voice-" + id, new BigDecimal(score),
                        new IntroAudio("test-bucket", "voice-intros/" + uuid + "/job-" + id + ".mp3",
                                "audio/mpeg", 1000L, 1000)), null);
    }

    @Test
    void completionPersistsProfileJobScoreAndIgnoresDuplicate() {
        service.handle(completed(jobId, "82.35"));
        service.handle(completed(jobId, "10"));
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.COMPLETED);
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).hasSize(1);
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId).get(0).getIntroAudioObjectKey())
                .endsWith("job-" + jobId + ".mp3");
        assertThat(clones.findById(cloneId).orElseThrow().getVoiceSimilarityScore())
                .isEqualByComparingTo("82.35");
    }

    @Test
    void lateOlderCompletionCannotReplaceNewerProfileOrScore() {
        Long newerId = new TransactionTemplate(transactions).execute(tx ->
                jobs.saveAndFlush(VoiceTrainingJob.create(users.findById(userId).orElseThrow())).getId());
        service.handle(completed(newerId, "91"));
        service.handle(completed(jobId, "30"));
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).singleElement()
                .extracting(profile -> profile.getVoiceTrainingJob().getId()).isEqualTo(newerId);
        assertThat(clones.findById(cloneId).orElseThrow().getVoiceSimilarityScore()).isEqualByComparingTo("91");
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.COMPLETED);
    }

    @Test
    void newerCompletionDeactivatesPreviousProfile() {
        service.handle(completed(jobId, "82"));
        Long newerId = new TransactionTemplate(transactions).execute(tx ->
                jobs.saveAndFlush(VoiceTrainingJob.create(users.findById(userId).orElseThrow())).getId());
        service.handle(completed(newerId, "91"));
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).singleElement()
                .extracting(profile -> profile.getVoiceTrainingJob().getId()).isEqualTo(newerId);
        assertThat(profiles.countByCloneId(cloneId)).isEqualTo(2);
        assertThat(clones.findById(cloneId).orElseThrow().getVoiceSimilarityScore()).isEqualByComparingTo("91");
    }

    @Test
    void failedJobKeepsPreviousActiveProfile() {
        service.handle(completed(jobId, "82"));
        Long newerId = new TransactionTemplate(transactions).execute(tx ->
                jobs.saveAndFlush(VoiceTrainingJob.create(users.findById(userId).orElseThrow())).getId());
        service.handle(new VoiceTrainingResultDTO("VOICE_TRAINING_STATUS", newerId, uuid, cloneId,
                "FAILED", null, new Failure("MODEL_ERROR", "retrying", true)));
        assertThat(jobs.findById(newerId).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.PROCESSING);
        service.handle(new VoiceTrainingResultDTO("VOICE_TRAINING_STATUS", newerId, uuid, cloneId,
                "FAILED", null, new Failure("MODEL_ERROR", "failed", false)));
        assertThat(jobs.findById(newerId).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.FAILED);
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).hasSize(1);
    }

    @Test
    void rollbackAndBadOwnerDoNotConsumeResult() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.handle(completed(jobId, "82"));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.PENDING);
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).isEmpty();
        var wrongOwner = new VoiceTrainingResultDTO("VOICE_TRAINING_STATUS", jobId,
                UUID.randomUUID(), cloneId, "COMPLETED", completed(jobId, "82").result(), null);
        assertThatThrownBy(() -> service.handle(wrongOwner)).isInstanceOf(IllegalArgumentException.class);
        service.handle(completed(jobId, "82"));
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).hasSize(1);
    }
}
