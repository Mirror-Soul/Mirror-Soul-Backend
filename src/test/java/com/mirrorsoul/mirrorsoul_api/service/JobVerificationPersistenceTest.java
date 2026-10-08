package com.mirrorsoul.mirrorsoul_api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql = false)
@Import({JobVerificationRequestWriter.class, JpaAuditingConfig.class})
class JobVerificationPersistenceTest {

    @Autowired private JobVerificationRequestWriter writer;
    @Autowired private JobVerificationRequestRepository requests;
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsPendingRequestWithMultipleOrderedPhotos() {
        UUID uuid = UUID.randomUUID();
        User user = users.saveAndFlush(User.builder()
                .uuid(uuid)
                .email(uuid + "@example.com")
                .passwordHash("hash")
                .job(Job.IT_TECH)
                .status(UserStatus.ONBOARD_B)
                .build());
        List<JobVerificationRequest.FileSnapshot> images = List.of(
                new JobVerificationRequest.FileSnapshot("bucket", "photo-a", "v1", "\"etag-a\""),
                new JobVerificationRequest.FileSnapshot("bucket", "photo-b", "v2", "\"etag-b\"")
        );

        var response = writer.createPending(uuid, images);
        entityManager.flush();
        entityManager.clear();

        JobVerificationRequest saved = requests.findById(response.requestId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getClaimedJob()).isEqualTo(Job.IT_TECH);
        assertThat(saved.getStatus()).isEqualTo(JobVerificationRequestStatus.PENDING);
        assertThat(saved.getFiles()).extracting(file -> file.getObjectKey())
                .containsExactly("photo-a", "photo-b");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
