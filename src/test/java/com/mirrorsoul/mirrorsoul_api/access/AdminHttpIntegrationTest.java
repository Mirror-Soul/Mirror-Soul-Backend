package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.common.jwt.TokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@SpringBootTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","spring.datasource.url=jdbc:h2:mem:admin_http;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","management.cloudwatch.metrics.export.enabled=false","monitoring.cloudwatch.enabled=false"})
@AutoConfigureMockMvc
class AdminHttpIntegrationTest {
 @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired TokenProvider tokens;@Autowired AdminAuditEventRepository audits;
 @MockitoBean software.amazon.awssdk.services.s3.S3Client s3;
 @MockitoBean software.amazon.awssdk.services.sqs.SqsClient sqs;
 User user(UserRole role) {return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash").status(UserStatus.ACTIVE).role(role).build());}
 @Test void realSecurityChainChecksStaffAccessAndAuditsBeforeAndAfterMvc() throws Exception {
  var root=user(UserRole.SUPER_ADMIN);var ordinary=user(UserRole.USER);
  mvc.perform(get("/admin/me").header("Authorization","Bearer "+tokens.createAccessToken(root))).andExpect(status().isOk()).andExpect(jsonPath("$.result.staffId").value(root.getId())).andExpect(jsonPath("$.result.permissions").isArray());
  mvc.perform(get("/admin/members").header("Authorization","Bearer "+tokens.createAccessToken(root))).andExpect(status().isOk());
  mvc.perform(get("/admin/me").header("Authorization","Bearer "+tokens.createAccessToken(ordinary))).andExpect(status().isForbidden());
  mvc.perform(get("/admin/me").header("Authorization","Bearer "+tokens.createAccessToken(root)).header("Origin","https://evil.example")).andExpect(status().isForbidden());
  String oldToken=tokens.createAccessToken(root);root.revokeAdministrativeSessions();users.saveAndFlush(root);
  mvc.perform(get("/admin/me").header("Authorization","Bearer "+oldToken)).andExpect(status().isForbidden());
  assertThat(audits.findAll().stream().anyMatch(e->root.getId().equals(e.getStaffId())&&"DENIED".equals(e.getResult()))).isTrue();
  assertThat(audits.findAll().stream().filter(e->root.getId().equals(e.getStaffId())&&"SUCCESS".equals(e.getResult())).count()).isGreaterThanOrEqualTo(2);
  assertThat(audits.findAll().stream().anyMatch(e->ordinary.getId().equals(e.getStaffId())&&"DENIED".equals(e.getResult()))).isTrue();
 }
 @Autowired com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository jobRequests;
 @Test void jobHistoryAndRetryUseRealPermissionChecksAndAuditAdvisor() throws Exception {
  var root=user(UserRole.SUPER_ADMIN);var viewer=user(UserRole.ADMIN_VIEWER);var owner=user(UserRole.USER);
  var request=com.mirrorsoul.mirrorsoul_api.domain.JobVerificationRequest.pending(owner,Job.IT_TECH);
  request.approve(root,java.time.LocalDateTime.now());request.startRetention(java.time.LocalDateTime.now(),30);
  request.notification("UNKNOWN","DELIVERY_INTERRUPTED",java.time.LocalDateTime.now());
  request=jobRequests.saveAndFlush(request);Long id=request.getId();
  mvc.perform(get("/admin/job-verifications/history").param("requestId",id.toString()).header("Authorization","Bearer "+tokens.createAccessToken(root)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.result.content[0].requestId").value(id));
  mvc.perform(get("/admin/job-verifications/history").header("Authorization","Bearer "+tokens.createAccessToken(viewer)))
   .andExpect(status().isForbidden());
  mvc.perform(post("/admin/job-verifications/"+id+"/notification/retry").header("Authorization","Bearer "+tokens.createAccessToken(root))
   .contentType("application/json").content("{\"version\":"+request.getVersion()+",\"possibleDuplicateAcknowledged\":true}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.result.notificationState").value("PENDING"));
  assertThat(audits.findAll().stream().anyMatch(e->root.getId().equals(e.getStaffId())&&"JobReviewExtensionController.retry".equals(e.getAction())&&"SUCCESS".equals(e.getResult()))).isTrue();
 }
}
