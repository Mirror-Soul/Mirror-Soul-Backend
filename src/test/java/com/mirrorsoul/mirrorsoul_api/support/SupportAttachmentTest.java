package com.mirrorsoul.mirrorsoul_api.support;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import software.amazon.awssdk.core.ResponseBytes;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({SupportTicketService.class,SupportAccess.class,SupportDiagnostics.class,SupportAttachmentService.class,SupportAttachmentTest.Config.class})
class SupportAttachmentTest {
 @Autowired UserRepository users;
 @Autowired SupportTicketService tickets;
 @Autowired SupportAttachmentService service;
 @Autowired SupportAttachmentRepository repository;
 @Autowired S3Client s3;
 @Autowired S3Presigner presigner;
 @org.springframework.boot.test.context.TestConfiguration static class Config {
  @Bean S3Client s3() {return mock(S3Client.class);}
  @Bean S3Presigner presigner() {return mock(S3Presigner.class);}
  @Bean AwsS3Properties properties() {var p=new AwsS3Properties();p.setBucket("private-support");p.setRegion("ap-northeast-2");return p;}
 }
 @BeforeEach void resetMocks() throws Exception {
  reset(s3,presigner);
  when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(PresignedPutObjectRequest.builder().expiration(Instant.now().plusSeconds(300)).httpRequest(software.amazon.awssdk.http.SdkHttpFullRequest.builder().uri(java.net.URI.create("https://private.example/upload")).method(software.amazon.awssdk.http.SdkHttpMethod.PUT).build()).isBrowserExecutable(false).signedHeaders(Map.of("x-amz-server-side-encryption",List.of("AES256"))).build());
 }
 User user(UserRole role) {return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash").status(UserStatus.ACTIVE).role(role).createdAt(LocalDateTime.now()).build());}
 TicketView ticket(User member) {return tickets.create(member.getId(),new Create(Category.OTHER,"subject","body",null,null,null,null,"create"));}
 @Test void uploadIsPrivateSizedAndRestrictedToTicketOwner() {
  var member=user(UserRole.USER);var other=user(UserRole.USER);var t=ticket(member);
  assertThatThrownBy(()->service.upload(other.getId(),t.id(),new Upload("file.png","image/png",8))).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.upload(member.getId(),t.id(),new Upload("file.html","text/html",8))).isInstanceOf(GeneralException.class);
  var upload=service.upload(member.getId(),t.id(),new Upload("file.png","image/png",8));
  assertThat(upload.signedHeaders()).containsKey("x-amz-server-side-encryption");
  var a=repository.findById(upload.id()).orElseThrow();assertThat(a.getObjectKey()).startsWith("support-private/"+t.id()+"/");
  assertThatThrownBy(()->service.download(member.getId(),t.id(),a.getId())).isInstanceOf(GeneralException.class);
  verifyNoInteractions(s3);
 }
 @Test void confirmationValidatesSignatureAndExpiredAttachmentsCannotBeAccessed() {
  var member=user(UserRole.USER);var t=ticket(member);var upload=service.upload(member.getId(),t.id(),new Upload("file.png","image/png",8));var a=repository.findById(upload.id()).orElseThrow();
  ReflectionTestUtils.setField(a,"uploadExpiresAt",SupportAccess.now().minusSeconds(1));
  when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(8L).contentType("image/png").eTag("etag").build());
  when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),new byte[]{1,2,3,4,5,6,7,8}));
  assertThatThrownBy(()->service.confirm(member.getId(),t.id(),a.getId())).isInstanceOf(GeneralException.class);assertThat(a.isConfirmed()).isFalse();
  when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(),new byte[]{(byte)137,80,78,71,13,10,26,10}));
  service.confirm(member.getId(),t.id(),a.getId());assertThat(a.getEtag()).isEqualTo("etag");
  ReflectionTestUtils.setField(a,"expiresAt",SupportAccess.now().minusSeconds(1));assertThatThrownBy(()->service.download(member.getId(),t.id(),a.getId())).isInstanceOf(GeneralException.class);
 }
 @Test void adminViewerAndUnassignedOperatorCannotAccessAttachment() {
  var member=user(UserRole.USER);var viewer=user(UserRole.ADMIN_VIEWER);var admin=user(UserRole.ADMIN);var t=ticket(member);
  var upload=service.upload(member.getId(),t.id(),new Upload("file.png","image/png",8));var a=repository.findById(upload.id()).orElseThrow();a.confirm(null,"etag");
  assertThatThrownBy(()->service.download(viewer.getId(),t.id(),a.getId())).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.download(admin.getId(),t.id(),a.getId())).isInstanceOf(GeneralException.class);
  verify(presigner,never()).presignGetObject(any(GetObjectPresignRequest.class));
 }
 @Test void failedCleanupDoesNotMarkObjectDeletedAndCanRetry() {
  var member=user(UserRole.USER);var t=ticket(member);var upload=service.upload(member.getId(),t.id(),new Upload("file.png","image/png",8));var a=repository.findById(upload.id()).orElseThrow();
  ReflectionTestUtils.setField(a,"expiresAt",SupportAccess.now().minusSeconds(1));
  when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
  assertThatThrownBy(()->service.deleteExpired(a.getId())).isInstanceOf(S3Exception.class);assertThat(a.isDeleted()).isFalse();
  when(s3.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());service.deleteExpired(a.getId());assertThat(a.isDeleted()).isTrue();
 }
}
