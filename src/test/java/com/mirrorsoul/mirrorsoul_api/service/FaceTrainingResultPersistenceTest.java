package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.*;

import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.dto.visual.FaceTrainingResultDTO.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.util.UUID;
import java.util.concurrent.*;
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
@Import({FaceTrainingResultService.class, CloneReadinessService.class, JpaAuditingConfig.class,
        FaceTrainingResultPersistenceTest.StorageConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FaceTrainingResultPersistenceTest {
    @TestConfiguration
    static class StorageConfig {
        @Bean AwsS3Properties storage() {
            var storage = new AwsS3Properties();
            storage.setBucket("test-bucket");
            return storage;
        }
    }

    @Autowired FaceTrainingResultService service;
    @Autowired CloneReadinessService readiness;
    @Autowired UserRepository users;
    @Autowired CloneRepository clones;
    @Autowired FaceTrainingJobRepository jobs;
    @Autowired AiFaceProfileRepository profiles;
    @Autowired VoiceTrainingJobRepository voiceJobs;
    @Autowired AiVoiceProfileRepository voices;
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
            cloneId = clones.saveAndFlush(Clone.builder().user(user).syncRate(0).build()).getId();
            jobId = jobs.saveAndFlush(FaceTrainingJob.create(user, FaceTrainingJobSource.ONBOARDING_FACE)).getId();
        });
    }

    private FaceTrainingResultDTO completed() {
        String prefix = "face-results/" + uuid + "/job-" + jobId + "/";
        return new FaceTrainingResultDTO("FACE_PROFILE_BUILD_STATUS", jobId, uuid, cloneId, "COMPLETED",
                new Result("READY_FOR_RENDERING", new Artifacts("test-bucket", prefix + "face-profile.json",
                        prefix + "portrait.jpg", prefix + "preprocess-manifest.json", null), true), null);
    }

    @Test
    void concurrentRedeliveryCreatesOneProfileAndLateVoiceAllowsReadiness() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> delivery = () -> { start.await(); service.handle(completed()); return null; };
            Future<Void> first = executor.submit(delivery);
            Future<Void> second = executor.submit(delivery);
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).hasSize(1);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(FaceTrainingJobStatus.COMPLETED);
        readiness.updatePersonalityTraining(cloneId, true);
        assertThat(clones.findById(cloneId).orElseThrow().getStatus()).isEqualTo("PENDING");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            User user = users.findById(userId).orElseThrow();
            VoiceTrainingJob voiceJob = voiceJobs.saveAndFlush(VoiceTrainingJob.create(user));
            voices.saveAndFlush(AiVoiceProfile.create(clones.findById(cloneId).orElseThrow(), voiceJob,
                    "voice-1", "ACTIVE", true));
        });
        readiness.refresh(cloneId);
        assertThat(clones.findById(cloneId).orElseThrow().getStatus()).isEqualTo("READY");
    }

    @Test
    void downstreamRollbackLeavesNoCompletedJobOrProfile() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.handle(completed());
            throw new IllegalStateException("transaction failed before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(FaceTrainingJobStatus.PENDING);
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).isEmpty();
        service.handle(completed());
        assertThat(profiles.findAllByCloneIdAndActiveTrue(cloneId)).hasSize(1);
    }
}
