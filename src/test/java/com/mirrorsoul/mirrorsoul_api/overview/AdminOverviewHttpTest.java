package com.mirrorsoul.mirrorsoul_api.overview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mirrorsoul.mirrorsoul_api.access.*;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.*;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.quality.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.safety.*;
import com.mirrorsoul.mirrorsoul_api.support.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@SpringBootTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:overview_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "management.cloudwatch.metrics.export.enabled=false","monitoring.cloudwatch.enabled=false"})
@AutoConfigureMockMvc
class AdminOverviewHttpTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CloneRepository clones;
    @Autowired JobVerificationRequestRepository documents;
    @Autowired SafetyReportRepository reports;
    @MockitoSpyBean SupportTicketRepository support;
    @Autowired VoiceTrainingJobRepository voices;
    @Autowired FaceTrainingJobRepository faces;
    @Autowired CloneProfileGenerationJobRepository profiles;
    @Autowired RagProfileJobRepository rag;
    @Autowired TrainingLifecycleRepository events;
    @Autowired TrainingRetryRepository retries;
    @Autowired StaffAccountRepository staff;
    @Autowired AdminAuditEventRepository audits;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meters;
    @MockitoBean software.amazon.awssdk.services.s3.S3Client s3;
    @MockitoBean software.amazon.awssdk.services.sqs.SqsClient sqs;

    User user(UserRole role) {return users.saveAndFlush(User.builder().uuid(UUID.randomUUID()).email(UUID.randomUUID()+"@example.com")
            .passwordHash("private-password").status(UserStatus.ACTIVE).role(role).build());}
    String token(User user) {return "Bearer "+tokens.createAccessToken(user);}
    JsonNode overview(User user,OffsetDateTime from,OffsetDateTime to) throws Exception {
        var request=get("/admin/overview").header("Authorization",token(user));
        if(from!=null)request.param("from",from.toString());if(to!=null)request.param("to",to.toString());
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("result");
    }
    void created(String table,Long id,OffsetDateTime at) {
        jdbc.update("UPDATE "+table+" SET created_at=?,updated_at=? WHERE id=?",AdminListWindow.local(at),AdminListWindow.local(at),id);
    }
    VoiceTrainingJob voice(User owner,boolean failed,OffsetDateTime at) {
        var j=VoiceTrainingJob.create(owner);if(failed)j.markDispatchFailed("private error");voices.saveAndFlush(j);
        created("voice_training_jobs",j.getId(),at);return j;
    }
    @Test void everyCountMatchesItsRealDrilldownAcrossKindsStatesAndExclusivePeriodBounds() throws Exception {
        var root=user(UserRole.SUPER_ADMIN);var owner=user(UserRole.USER);
        var clone=clones.saveAndFlush(Clone.builder().user(owner).createdAt(LocalDateTime.now()).build());
        var from=OffsetDateTime.parse("2020-01-01T00:00:00+09:00");var to=from.plusDays(1);
        var pending=documents.saveAndFlush(JobVerificationRequest.pending(owner,Job.IT_TECH));created("job_verification_requests",pending.getId(),from);
        var upper=documents.saveAndFlush(JobVerificationRequest.pending(owner,Job.IT_TECH));created("job_verification_requests",upper.getId(),to);
        for(var state:SafetyTypes.ReportState.values()) {
            var r=new SafetyReport(owner.getId(),root.getId(),SafetyTypes.ReportType.THREATS,SafetyTypes.Priority.URGENT,
                    null,null,"private description",null,UUID.randomUUID().toString(),"hash","context",AdminListWindow.local(from.plusHours(1)),30);
            if(state==SafetyTypes.ReportState.REVIEWING)r.assign(root.getId(),LocalDateTime.now());
            if(state==SafetyTypes.ReportState.DECIDED)r.decide(1L,LocalDateTime.now());
            if(state==SafetyTypes.ReportState.APPEALED)r.appeal(LocalDateTime.now());reports.saveAndFlush(r);
        }
        reports.saveAndFlush(new SafetyReport(owner.getId(),root.getId(),SafetyTypes.ReportType.OTHER,SafetyTypes.Priority.HIGH,
                null,null,"private",null,UUID.randomUUID().toString(),"hash","context",AdminListWindow.local(from.plusHours(1)),30));
        for(var state:SupportTypes.Status.values()) {
            var input=new SupportDtos.Create(SupportTypes.Category.OTHER,"private subject","private body",null,null,null,null,UUID.randomUUID().toString());
            var t=new SupportTicket(owner.getId(),SupportTypes.Channel.APP,input,"hash",null,AdminListWindow.local(from.plusHours(2)));
            t.status(state,LocalDateTime.now());support.saveAndFlush(t);
        }
        voice(owner,true,from.plusHours(3));
        var retired=voice(owner,true,from.plusHours(3));var replacement=voice(owner,false,from.plusHours(3));
        jdbc.update("UPDATE voice_training_jobs SET updated_at=? WHERE id=?",LocalDateTime.now(),replacement.getId());
        retries.saveAndFlush(new TrainingRetryCommand(UUID.randomUUID().toString(),"VOICE-"+retired.getId(),"VOICE-"+replacement.getId(),root.getId(),1));
        var face=FaceTrainingJob.create(owner,FaceTrainingJobSource.FACE_UPDATE);face.markDispatchFailed("private");faces.saveAndFlush(face);
        created("face_training_jobs",face.getId(),from.plusHours(3));
        var profile=CloneProfileGenerationJob.create(clone,CloneProfileTrigger.PERSONALITY_UPDATED,"a".repeat(64),"prompt");
        profile.fail(LocalDateTime.now(),"UNEXPECTED_ERROR","private prompt");profiles.saveAndFlush(profile);
        created("clone_profile_generation_jobs",profile.getId(),from.plusHours(3));
        var r=RagProfileJob.create(clone.getId(),AdminListWindow.local(from.plusHours(3)));r.request(AdminListWindow.local(from.plusHours(3)));
        r.trainingFailed("TRAINING_FAILED",true,LocalDateTime.now());rag.saveAndFlush(r);
        voice(owner,false,from.plusHours(4));
        for(boolean accepted:new boolean[]{true,false}) {
            var v=voice(owner,false,from.plusHours(4));String key="VOICE-"+v.getId();
            var event=new TrainingDtos.Lifecycle(UUID.randomUUID().toString(),1L,TrainingDtos.State.PROGRESS,TrainingDtos.Stage.TRAINING,
                    OffsetDateTime.now(),clone.getId(),0L,QualitySupport.correlation(key),null,null);
            events.saveAndFlush(new TrainingLifecycleEvent(UUID.randomUUID().toString(),key,"hash",event,accepted));
        }
        var pendingFace=faces.saveAndFlush(FaceTrainingJob.create(owner,FaceTrainingJobSource.FACE_UPDATE));
        created("face_training_jobs",pendingFace.getId(),from.plusHours(4));
        var response=overview(root,from,to);
        var expected=Map.of("PENDING_DOCUMENTS",1L,"URGENT_REPORTS",3L,"UNANSWERED_SUPPORT",2L,"FAILED_TRAINING_JOBS",4L,"STALLED_TRAINING_JOBS",3L);
        assertThat(response.get("dataState").asString()).isEqualTo("COMPLETE");
        for(var card:response.get("cards")) {
            assertThat(card.get("count").asLong()).as(card.get("key").asString()).isEqualTo(expected.get(card.get("key").asString()));
            String href=card.get("drilldown").get("href").asString();assertThat(href).contains("%2B09%3A00");
            var list=json.readTree(mvc.perform(get(URI.create(href)).header("Authorization",token(root)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("result");
            assertThat(list.get("totalElements").asLong()).isEqualTo(card.get("count").asLong());
        }
        assertThat(response.toString()).doesNotContain("private subject","private body","private prompt","private description","private-password");
    }
    @Test void permissionsControlCardsAndAreRecheckedAfterStaffChanges() throws Exception {
        var viewer=user(UserRole.ADMIN_VIEWER);
        var response=overview(viewer,null,null);
        assertThat(response.get("cards")).hasSize(4);
        assertThat(response.toString()).doesNotContain("PENDING_DOCUMENTS");
        var limited=user(UserRole.ADMIN);
        var account=staff.saveAndFlush(new StaffAccount(limited.getId(),"limited",Set.of(AdminPermission.support_read),StaffAccount.State.ACTIVE,LocalDateTime.now()));
        assertThat(overview(limited,null,null).get("cards")).hasSize(1);
        account.permissions(Set.of(),LocalDateTime.now());account=staff.saveAndFlush(account);
        assertThat(overview(limited,null,null).get("cards")).isEmpty();
        account.revoke(LocalDateTime.now());staff.saveAndFlush(account);
        mvc.perform(get("/admin/overview").header("Authorization",token(limited))).andExpect(status().isForbidden());
    }
    @SuppressWarnings("unchecked")
    @Test void realReadTransactionFailureDoesNotHideOtherCardsOrBecomeAZeroCount() throws Exception {
        var root=user(UserRole.SUPER_ADMIN);
        doThrow(new DataAccessResourceFailureException("private SQL host and credentials")).when(support).count(any(Specification.class));
        var response=overview(root,null,null);
        assertThat(response.get("dataState").asString()).isEqualTo("PARTIAL");
        for(var card:response.get("cards")) {
            if(card.get("key").asString().equals("UNANSWERED_SUPPORT")) {
                assertThat(card.get("count").isNull()).isTrue();assertThat(card.get("dataState").asString()).isEqualTo("UNAVAILABLE");
            } else assertThat(card.get("dataState").asString()).isEqualTo("READY");
        }
        assertThat(response.toString()).doesNotContain("private SQL host and credentials");
        assertThat(meters.find("admin.overview.card.failures").tag("card","UNANSWERED_SUPPORT").counter().count()).isGreaterThanOrEqualTo(1);
    }
    @Test void successfulEmptyPeriodIsZeroAndAuthenticationOriginAndRangeAreEnforced() throws Exception {
        var root=user(UserRole.SUPER_ADMIN);var ordinary=user(UserRole.USER);
        var from=OffsetDateTime.parse("1900-01-01T00:00:00Z");var to=from.plusDays(1);
        var response=overview(root,from,to);
        for(var card:response.get("cards")) {
            assertThat(card.get("count").asLong()).isZero();assertThat(card.get("dataState").asString()).isEqualTo("READY");
        }
        mvc.perform(get("/admin/overview")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/overview").header("Authorization",token(ordinary))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/overview").header("Authorization",token(root)).header("Origin","https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/overview").header("Authorization",token(root)).param("from",to.toString()).param("to",from.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/overview").header("Authorization",token(root)).param("from","private-invalid-date"))
                .andExpect(status().isBadRequest());
        assertThat(audits.findAll()).anyMatch(a->root.getId().equals(a.getStaffId())&&"AdminOverviewController.get".equals(a.getAction()));
    }
}
