package com.mirrorsoul.mirrorsoul_api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mirrorsoul.mirrorsoul_api.common.mail.EmailAuthConst;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.TalkTimeTransactionReason;
import com.mirrorsoul.mirrorsoul_api.repository.CloneRepository;
import com.mirrorsoul.mirrorsoul_api.repository.TalkTimeTransactionRepository;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:join_signup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "management.cloudwatch.metrics.export.enabled=false",
        "monitoring.cloudwatch.enabled=false"
})
@AutoConfigureMockMvc
class JoinSignupIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private CloneRepository clones;
    @Autowired private TalkTimeTransactionRepository talkTimeTransactions;
    @MockitoBean private S3Client s3;
    @MockitoBean private SqsClient sqs;

    @Test
    void signupPersistsUserCloneAndInitialTalkTimeGrant() throws Exception {
        String email = "join-signup@example.com";
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_TARGET, email);
        session.setAttribute(EmailAuthConst.EMAIL_AUTH_VERIFIED, true);

        ResultActions response = mvc.perform(post("/join/basic-profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "password1",
                                    "gender": null,
                                    "birthDate": null,
                                    "termsAgreed": true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty());

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getRefreshToken()).isNotBlank();
        response.andExpect(jsonPath("$.result.userUuid").value(user.getUuid().toString()))
                .andExpect(jsonPath("$.result.refreshToken").value(user.getRefreshToken()));
        assertThat(clones.findByUserUuid(user.getUuid())).isPresent();
        assertThat(talkTimeTransactions.findByUserIdOrderByIdDesc(
                user.getId(), PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(grant -> {
                    assertThat(grant.getReason()).isEqualTo(TalkTimeTransactionReason.SIGNUP_GRANT);
                    assertThat(grant.getDeltaSeconds()).isEqualTo(1800);
                    assertThat(grant.getBalanceAfterSeconds()).isEqualTo(1800);
                });
    }
}
