package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.access.AccessDtos.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.transaction.annotation.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({StaffService.class,AdminPermissionService.class,AdminAuditWriter.class,AdminReauthLimiter.class,StaffWorkflowTest.Config.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class StaffWorkflowTest {
 @Autowired UserRepository users;@Autowired StaffService service;@Autowired AdminPermissionService permissions;@Autowired AdminAuditEventRepository audit;@Autowired StaffAccountRepository staff;@Autowired PasswordEncoder passwords;
 @org.springframework.boot.test.context.TestConfiguration static class Config {
  @Bean PasswordEncoder passwords() {return new BCryptPasswordEncoder(4);}
  @Bean AdminInviteTokenCipher cipher() {return new AdminInviteTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));}
 }
 User user(UserRole role) {return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash(passwords.encode("password")).status(UserStatus.ACTIVE).role(role).createdAt(LocalDateTime.now()).build());}
 String reauth(User u) {return service.reauthenticate(u.getId(),new Reauthenticate("password")).token();}
 @Test void invitationIsEncryptedIdempotentAndActivationInvalidatesOriginalSession() {
  var root=user(UserRole.SUPER_ADMIN);var member=user(UserRole.USER);var request=new Invite(member.getId(),"operator",Set.of(AdminPermission.support_read,AdminPermission.support_reply),"invite-one");
  var token=reauth(root);var invite=service.invite(root.getId(),request,token);assertThat(invite.staff().accountState()).isEqualTo("INVITED");
  assertThat(service.invite(root.getId(),request,token).activationToken()).isEqualTo(invite.activationToken());
  assertThat(staff.findById(member.getId()).orElseThrow().getInviteCipher()).doesNotContain(invite.activationToken());
  assertThatThrownBy(()->permissions.active(member.getId())).isInstanceOf(GeneralException.class);
  service.activate(member.getId(),invite.activationToken());assertThat(users.findById(member.getId()).orElseThrow().getSessionVersion()).isGreaterThan(member.getSessionVersion());
  assertThat(permissions.permissions(member.getId())).containsExactlyInAnyOrder(AdminPermission.support_read,AdminPermission.support_reply);
  assertThatThrownBy(()->service.activate(member.getId(),invite.activationToken())).isInstanceOf(GeneralException.class);
 }
 @Test void permissionChangeRequiresReauthenticationAndRevokesTokens() {
  var root=user(UserRole.SUPER_ADMIN);var admin=user(UserRole.ADMIN);var grant=new Grant(Set.of(AdminPermission.member_read),0L,"grant-one");
  assertThatThrownBy(()->service.grant(root.getId(),admin.getId(),grant,null)).isInstanceOf(GeneralException.class);
  var token=reauth(root);var updated=service.grant(root.getId(),admin.getId(),grant,token);assertThat(updated.permissions()).containsExactly(AdminPermission.member_read);
  assertThat(users.findById(admin.getId()).orElseThrow().getSessionVersion()).isGreaterThan(admin.getSessionVersion());
  assertThat(service.grant(root.getId(),admin.getId(),grant,token).permissions()).containsExactly(AdminPermission.member_read);
  assertThatThrownBy(()->service.grant(root.getId(),admin.getId(),new Grant(Set.of(AdminPermission.support_reply),updated.version(),"grant-two"),token)).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->permissions.require(admin.getId(),AdminPermission.support_reply)).isInstanceOf(GeneralException.class);
 }
 @Test void revokeRemovesStaffAccessAndRootCannotRemoveSelfOrAnotherRoot() {
  var root=user(UserRole.SUPER_ADMIN);var admin=user(UserRole.ADMIN);var result=service.revoke(root.getId(),admin.getId(),new Revoke(0L,"revoke"),reauth(root));
  assertThat(result.accountState()).isEqualTo("REVOKED");assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
  assertThatThrownBy(()->permissions.active(admin.getId())).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.revoke(root.getId(),root.getId(),new Revoke(0L,"self"),reauth(root))).isInstanceOf(GeneralException.class);
 }
 @Test void activePermissionsAreCheckedEveryTimeAndPasswordAttemptsAreLimited() {
  var root=user(UserRole.SUPER_ADMIN);var viewer=user(UserRole.ADMIN_VIEWER);
  assertThat(permissions.permissions(viewer.getId())).doesNotContain(AdminPermission.audit_read,AdminPermission.job_review,AdminPermission.support_reply);
  for(int i=0;i<5;i++) assertThatThrownBy(()->service.reauthenticate(root.getId(),new Reauthenticate("wrong"))).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.reauthenticate(root.getId(),new Reauthenticate("password"))).isInstanceOf(GeneralException.class).satisfies(e->assertThat(((GeneralException)e).getCode().getCode()).isEqualTo("STAFF_4290"));
 }
 @Test void staffListIncludesInvitationsAndRevokedUsers() {
  var root=user(UserRole.SUPER_ADMIN);var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);
  service.invite(root.getId(),new Invite(member.getId(),"pending",Set.of(AdminPermission.member_read),"invite"),reauth(root));
  service.revoke(root.getId(),admin.getId(),new Revoke(0L,"revoke"),reauth(root));
  assertThat(service.list(root.getId(),0,100).getContent()).extracting(StaffView::staffId).contains(member.getId(),admin.getId());
 }
}
