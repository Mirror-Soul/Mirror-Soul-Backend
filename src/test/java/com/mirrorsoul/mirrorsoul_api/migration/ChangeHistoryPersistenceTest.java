package com.mirrorsoul.mirrorsoul_api.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileGenerationJob;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileGenerationJobRepository;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileTrigger;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileVersion;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileVersionRepository;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.dto.GeneratedCloneProfile;
import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import com.mirrorsoul.mirrorsoul_api.domain.TalkLog;
import com.mirrorsoul.mirrorsoul_api.domain.TalkLogRevision;
import com.mirrorsoul.mirrorsoul_api.domain.TalkTimeTransaction;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Speaker;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkLogRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkLogRevisionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import(JpaAuditingConfig.class)
class ChangeHistoryPersistenceTest {
    @Autowired UserRepository users;
    @Autowired CloneRepository clones;
    @Autowired VideoCallRepository calls;
    @Autowired TalkLogRepository talkLogs;
    @Autowired TalkLogRevisionRepository revisions;
    @Autowired TalkTimeTransactionRepository transactions;
    @Autowired CloneProfileGenerationJobRepository jobs;
    @Autowired CloneProfileVersionRepository versions;
    @Autowired EntityManager entityManager;

    @Test
    void persistsAllThreeChangeHistories() {
        UUID uuid = UUID.randomUUID();
        User user = users.saveAndFlush(User.builder().uuid(uuid).email(uuid + "@example.com")
                .passwordHash("hash").status(UserStatus.ACTIVE).build());
        Clone clone = clones.saveAndFlush(Clone.builder().user(user).build());
        VideoCall call = calls.saveAndFlush(VideoCall.builder().user(user).clone(clone)
                .roomId("call-" + uuid).mediaType(CallMediaType.VOICE).build());
        TalkLog talkLog = talkLogs.saveAndFlush(TalkLog.builder().videoCall(call)
                .speaker(Speaker.CLONE).message("original").startedAt(LocalDateTime.now()).build());

        user.addTalkTime(600);
        TalkTimeTransaction transaction = transactions.saveAndFlush(TalkTimeTransaction.record(
                user, null, TalkTimeTransactionReason.TOP_UP, 600));

        talkLog.updateMessage("corrected");
        TalkLogRevision revision = revisions.saveAndFlush(TalkLogRevision.record(
                talkLog, user, "original"));

        CloneProfileGenerationJob job = jobs.saveAndFlush(CloneProfileGenerationJob.create(
                clone, CloneProfileTrigger.INTERVIEW_UPDATED, "a".repeat(64), "v1"));
        CloneProfileVersion version = versions.saveAndFlush(CloneProfileVersion.record(
                clone, job, 1, new GeneratedCloneProfile("summary", List.of("curious", "kind"))));

        entityManager.clear();

        assertThat(transactions.findById(transaction.getId()).orElseThrow().getBalanceAfterSeconds())
                .isEqualTo(2400);
        assertThat(revisions.findById(revision.getId()).orElseThrow().getPreviousMessage())
                .isEqualTo("original");
        assertThat(versions.findById(version.getId()).orElseThrow().getPersonalityTags())
                .containsExactly("curious", "kind");
        assertThat(versions.findLatestVersionNumber(clone.getId())).isEqualTo(1);
    }
}
