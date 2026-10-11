package com.mirrorsoul.mirrorsoul_api.quality;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.service.VoiceTrainingJobPublisher;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:quality_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "management.cloudwatch.metrics.export.enabled=false","monitoring.cloudwatch.enabled=false",
        "internal.ai.api-key=quality-test-key","internal.ai.scopes=call_diagnostics,training_status"})
@AutoConfigureMockMvc
class QualityHttpTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CloneRepository clones;
    @Autowired VoiceTrainingJobRepository voices;
    @Autowired VideoCallRepository calls;
    @Autowired TokenProvider tokens;
    @Autowired ObjectMapper json;
    @MockitoBean software.amazon.awssdk.services.s3.S3Client s3;
    @MockitoBean software.amazon.awssdk.services.sqs.SqsClient sqs;
    @MockitoBean VoiceTrainingJobPublisher publisher;
    User user(UserRole role){return users.saveAndFlush(User.builder().uuid(UUID.randomUUID())
            .email(UUID.randomUUID()+"@example.com").passwordHash("secret-password")
            .status(UserStatus.ACTIVE).role(role).build());}
    String token(User u){return "Bearer "+tokens.createAccessToken(u);}
    Clone clone(User owner){return clones.saveAndFlush(Clone.builder().user(owner).createdAt(LocalDateTime.now()).build());}
    @Test void readPermissionsAndSeparateRetryPermissionApplyThroughRealSecurityChain() throws Exception {
        var owner=user(UserRole.USER);clone(owner);var admin=user(UserRole.ADMIN);var viewer=user(UserRole.ADMIN_VIEWER);
        var root=user(UserRole.SUPER_ADMIN);var job=VoiceTrainingJob.create(owner);
        job.addFile(null,"private-bucket","raw-voice-object");job.markDispatchFailed("secret raw error");voices.saveAndFlush(job);
        mvc.perform(get("/admin/clone-training-jobs").param("memberId",owner.getId().toString()).header("Authorization",token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.content[0].trainingJobId").value("VOICE-"+job.getId()))
                .andExpect(jsonPath("$.result.content[0].failureCode").value("DISPATCH_FAILED"));
        mvc.perform(get("/admin/clone-training-jobs").header("Authorization",token(owner))).andExpect(status().isForbidden());
        String route="/admin/clone-training-jobs/VOICE-"+job.getId()+"/retry";
        String body="{\"idempotencyKey\":\"quality-http-retry\"}";
        for(var denied:new User[]{viewer,admin}){
            mvc.perform(post(route).header("Authorization",token(denied)).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        var response=mvc.perform(post(route).header("Authorization",token(root)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.retryCount").value(1)).andReturn().getResponse().getContentAsString();
        long replacement=TrainingId.parse(json.readTree(response).get("result").get("newTrainingJobId").asString()).sourceId();
        verify(publisher).publish(replacement);
        mvc.perform(post(route).header("Authorization",token(root)).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.newTrainingJobId").value("VOICE-"+replacement));
    }
    @Test void internalAuthenticationValidationAndDefaultDiagnosticsAvoidRawContent() throws Exception {
        var owner=user(UserRole.USER);var clone=clone(owner);var viewer=user(UserRole.ADMIN_VIEWER);
        var call=VideoCall.builder().clone(clone).user(owner).roomId(UUID.randomUUID().toString()).mediaType(CallMediaType.VOICE).build();
        ReflectionTestUtils.setField(call,"startedAt",LocalDateTime.now().minusMinutes(1));calls.saveAndFlush(call);
        String route="/internal/ai/calls/"+call.getId()+"/diagnostics";
        mvc.perform(post(route).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(route).header("X-Internal-Api-Key","wrong").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        var payload=new CallQualityDtos.Event("first","turn",CallQualityDtos.Step.STT_ENDED,OffsetDateTime.now().minusSeconds(5),
                UUID.fromString(call.diagnosticCorrelationId()),clone.getId(),0L,null,null);
        String body=json.writeValueAsString(payload);
        mvc.perform(post(route).header("X-Internal-Api-Key","quality-test-key").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.duplicate").value(false));
        mvc.perform(post(route).header("X-Internal-Api-Key","quality-test-key").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.duplicate").value(true));
        String bad="{\"eventId\":\"secret raw voice\",\"step\":\"PRIVATE_SECRET\"}";
        var error=mvc.perform(post(route).header("X-Internal-Api-Key","quality-test-key").contentType("application/json").content(bad))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(error).doesNotContain("PRIVATE_SECRET","secret raw voice");
        var response=mvc.perform(get("/admin/calls/"+call.getId()+"/diagnostics").header("Authorization",token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.eventCount").value(1))
                .andExpect(jsonPath("$.result.distribution.stages[0].sampleCount").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret-password","voiceUrl","faceUrl","transcript","raw-voice-object");
        mvc.perform(get("/internal/ai/calls/"+call.getId()+"/context").header("X-Internal-Api-Key","quality-test-key"))
                .andExpect(status().isForbidden());
    }
}
