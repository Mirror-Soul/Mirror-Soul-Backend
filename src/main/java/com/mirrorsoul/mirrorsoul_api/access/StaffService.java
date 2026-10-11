package com.mirrorsoul.mirrorsoul_api.access;
import com.mirrorsoul.mirrorsoul_api.access.AccessDtos.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class StaffService {
 private final UserRepository users;
 private final StaffAccountRepository staff;
 private final AdminPermissionService permissions;
 private final AdminReauthenticationRepository reauths;
 private final AdminCommandRepository commands;
 private final AdminAuditWriter audit;
 private final AdminReauthLimiter limiter;
 private final PasswordEncoder passwords;
 private final AdminInviteTokenCipher cipher;
 @Transactional(readOnly=true) public StaffView me(Long actor) {permissions.active(actor);return view(users.findById(actor).orElseThrow());}
 @Transactional(readOnly=true) public Page<StaffView> list(Long actor,int page,int size) {
  permissions.require(actor,AdminPermission.staff_read);if(page<0||size<1||size>100) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
  return users.findAll((r,q,b)-> {var sub=q.subquery(Long.class);sub.select(sub.from(StaffAccount.class).get("userId"));return b.or(r.get("role").in(UserRole.ADMIN,UserRole.ADMIN_VIEWER,UserRole.SUPER_ADMIN),r.get("id").in(sub));},PageRequest.of(page,size,Sort.by("id"))).map(this::view);
 }
 public ReauthView reauthenticate(Long actor,Reauthenticate input) {
  permissions.require(actor,AdminPermission.staff_manage);limiter.attempt(actor);
  var u=superActor(actor);
  if(!passwords.matches(input.password(),u.getPasswordHash())) throw new GeneralException(GeneralErrorCode.STAFF_REAUTH_REQUIRED);
  String token=AccessSupport.token();var expiry=AccessSupport.now().plusMinutes(5);
  reauths.save(new AdminReauthentication(AccessSupport.hash(token),actor,u.getSessionVersion(),expiry));
  record(actor,"REAUTH","staff:"+actor);return new ReauthView(token,expiry);
 }
 public InviteView invite(Long actor,Invite input,String token) {
  superActor(actor);validate(input.permissions());String hash=AccessSupport.hash("INVITE",input.memberId(),input.displayName(),new TreeSet<>(input.permissions()));
  var previous=previous(actor,input.idempotencyKey(),hash);if(previous!=null) {var old=staff.findById(previous.getTargetId()).orElseThrow();return new InviteView(view(users.findById(previous.getTargetId()).orElseThrow()),old.getState()==StaffAccount.State.INVITED&&old.getInviteExpiresAt().isAfter(AccessSupport.now())?cipher.decrypt(old.getInviteCipher()):null,old.getInviteExpiresAt());}
  consume(actor,token);if(actor.equals(input.memberId())) AccessSupport.denied();
  var target=users.findByIdForUpdate(input.memberId()).orElseThrow(()->new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
  if(target.getRole()!=UserRole.USER||target.getStatus()!=UserStatus.ACTIVE) AccessSupport.conflict();
  validate(input.permissions());String activation=AccessSupport.token();var expiry=AccessSupport.now().plusHours(24);
  var s=staff.lock(target.getId()).orElse(null);if(s!=null&&s.getState()!=StaffAccount.State.REVOKED) AccessSupport.conflict();if(s==null) s=new StaffAccount(target.getId(),input.displayName(),input.permissions(),StaffAccount.State.INVITED,AccessSupport.now());else s.reinvite(input.displayName(),input.permissions(),AccessSupport.now());s.invite(AccessSupport.hash(activation),cipher.encrypt(activation),expiry);staff.saveAndFlush(s);
  commands.save(new AdminCommand(actor,input.idempotencyKey(),hash,target.getId()));record(actor,"STAFF_INVITE","staff:"+target.getId());return new InviteView(view(target),activation,expiry);
 }
 public StaffView activate(Long actor,String token) {
  var user=users.findByIdForUpdate(actor).orElseThrow(()->new GeneralException(GeneralErrorCode.FORBIDDEN));var s=staff.lock(actor).orElseThrow(()->new GeneralException(GeneralErrorCode.FORBIDDEN));
  if(user.getStatus()!=UserStatus.ACTIVE||s.getState()!=StaffAccount.State.INVITED||s.getInviteExpiresAt()==null||!s.getInviteExpiresAt().isAfter(AccessSupport.now())||!AccessSupport.hash(token).equals(s.getInviteHash())) AccessSupport.denied();
  s.activate(AccessSupport.now());user.changeAdministrativeRole(UserRole.ADMIN);staff.flush();record(actor,"STAFF_ACTIVATE","staff:"+actor);return view(user);
 }
 public StaffView grant(Long actor,Long targetId,Grant input,String token) {
  superActor(actor);validate(input.permissions());String hash=AccessSupport.hash("GRANT",targetId,input.version(),new TreeSet<>(input.permissions()));
  var prior=previous(actor,input.idempotencyKey(),hash);if(prior!=null) return view(users.findById(targetId).orElseThrow());consume(actor,token);
  var target=target(targetId,actor);var s=materialize(target);
  if(target.getRole()==UserRole.ADMIN_VIEWER&&!Set.of(AdminPermission.member_read,AdminPermission.moderation_read,AdminPermission.support_read,AdminPermission.audit_read,AdminPermission.staff_read).containsAll(input.permissions())) AccessSupport.denied();
  if(!Objects.equals(s.getVersion(),input.version())||s.getState()!=StaffAccount.State.ACTIVE) throw new GeneralException(GeneralErrorCode.STAFF_CONFLICT,"Staff version mismatch actual="+s.getVersion()+" expected="+input.version()+" state="+s.getState());validate(input.permissions());
  s.permissions(input.permissions(),AccessSupport.now());target.revokeAdministrativeSessions();staff.flush();commands.save(new AdminCommand(actor,input.idempotencyKey(),hash,targetId));record(actor,"STAFF_PERMISSIONS","staff:"+targetId);return view(target);
 }
 public StaffView revoke(Long actor,Long targetId,Revoke input,String token) {
  superActor(actor);String hash=AccessSupport.hash("REVOKE",targetId,input.version());var prior=previous(actor,input.idempotencyKey(),hash);if(prior!=null) return view(users.findById(targetId).orElseThrow());consume(actor,token);
  var target=target(targetId,actor);var s=materialize(target);if(!Objects.equals(s.getVersion(),input.version())) throw new GeneralException(GeneralErrorCode.STAFF_CONFLICT,"Staff version mismatch actual="+s.getVersion()+" expected="+input.version());s.revoke(AccessSupport.now());target.changeAdministrativeRole(UserRole.USER);staff.flush();commands.save(new AdminCommand(actor,input.idempotencyKey(),hash,targetId));record(actor,"STAFF_REVOKE","staff:"+targetId);return view(target);
 }
 private User superActor(Long id) {var u=users.findByIdForUpdate(id).orElseThrow(()->new GeneralException(GeneralErrorCode.FORBIDDEN));if(u.getRole()!=UserRole.SUPER_ADMIN||u.getStatus()!=UserStatus.ACTIVE) AccessSupport.denied();return u;}
 private User target(Long id,Long actor) {if(id.equals(actor)) AccessSupport.denied();var u=users.findByIdForUpdate(id).orElseThrow(()->new GeneralException(GeneralErrorCode.USER_NOT_FOUND));if(u.getRole()==UserRole.SUPER_ADMIN) AccessSupport.denied();return u;}
 private StaffAccount materialize(User u) {var s=staff.lock(u.getId());if(s.isPresent()) return s.get();if(u.getRole()==UserRole.USER) AccessSupport.denied();return staff.saveAndFlush(new StaffAccount(u.getId(),name(u),AdminPermissionService.defaults(u.getRole()),StaffAccount.State.ACTIVE,AccessSupport.now()));}
 private void validate(Set<AdminPermission> p) {if(p.stream().anyMatch(Objects::isNull)||p.contains(AdminPermission.staff_manage)) AccessSupport.denied();}
 private AdminCommand previous(Long actor,String key,String hash) {var c=commands.findByActorIdAndRequestKey(actor,key.toLowerCase(Locale.ROOT)).orElse(null);if(c!=null&&!hash.equals(c.getRequestHash())) AccessSupport.conflict();return c;}
 private void consume(Long actor,String token) {
  if(token==null||token.isBlank()) throw new GeneralException(GeneralErrorCode.STAFF_REAUTH_REQUIRED);
  var r=reauths.lock(AccessSupport.hash(token)).orElseThrow(()->new GeneralException(GeneralErrorCode.STAFF_REAUTH_REQUIRED));var u=users.findById(actor).orElseThrow();
  if(!actor.equals(r.getUserId())||r.isUsed()||!r.getExpiresAt().isAfter(AccessSupport.now())||!Objects.equals(u.getSessionVersion(),r.getSessionVersion())) throw new GeneralException(GeneralErrorCode.STAFF_REAUTH_REQUIRED);r.consume();
 }
 private String name(User u) {return u.getName()==null||u.getName().isBlank()?"staff-"+u.getId():u.getName();}
 private StaffView view(User u) {
  var s=staff.findById(u.getId()).orElse(null);String state=u.getStatus()!=UserStatus.ACTIVE?u.getStatus().name():s==null?"ACTIVE":s.getState().name();
  var perms=u.getStatus()==UserStatus.ACTIVE&&(s==null||s.getState()==StaffAccount.State.ACTIVE)?(s==null?AdminPermissionService.defaults(u.getRole()):u.getRole()==UserRole.SUPER_ADMIN?Set.of(AdminPermission.values()):Set.copyOf(s.getPermissions())):Set.<AdminPermission>of();
  return new StaffView(u.getId(),s==null?name(u):s.getDisplayName(),state,perms,s==null?0L:s.getVersion());
 }
 private void record(Long actor,String action,String target) {var c=AdminAuditContext.get();String reason=switch(action) {case "STAFF_INVITE"->"STAFF_INVITED";case "STAFF_ACTIVATE"->"INVITATION_ACCEPTED";case "STAFF_PERMISSIONS"->"ACCESS_POLICY_CHANGED";case "STAFF_REVOKE"->"STAFF_ACCESS_REVOKED";case "REAUTH"->"PASSWORD_REAUTHENTICATED";default->"OK";};audit.enlisted(actor,action,target,"SUCCESS",reason,c==null?UUID.randomUUID().toString():c.correlationId);}
}
