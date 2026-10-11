package com.mirrorsoul.mirrorsoul_api.access;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminPermissionService {
 private final UserRepository users;
 private final StaffAccountRepository staff;
 public User active(Long id) {
  var user=users.findById(id).orElseThrow(()->new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.FORBIDDEN));
  if(user.getStatus()!=UserStatus.ACTIVE||user.getRole()==UserRole.USER) AccessSupport.denied();
  var record=staff.findById(id);if(record.isPresent()&&record.get().getState()!=StaffAccount.State.ACTIVE) AccessSupport.denied();return user;
 }
 public Set<AdminPermission> permissions(Long id) {
  var user=active(id);if(user.getRole()==UserRole.SUPER_ADMIN) return Set.of(AdminPermission.values());
  var record=staff.findById(id);if(record.isPresent()) return Set.copyOf(record.get().getPermissions());
  return defaults(user.getRole());
 }
 public static Set<AdminPermission> defaults(UserRole role) {
  if(role==UserRole.SUPER_ADMIN) return Set.of(AdminPermission.values());
  if(role==UserRole.ADMIN) return Set.of(AdminPermission.job_review,AdminPermission.member_read,AdminPermission.member_manage,AdminPermission.moderation_read,AdminPermission.moderation,AdminPermission.support_read,AdminPermission.support_reply);
  if(role==UserRole.ADMIN_VIEWER) return Set.of(AdminPermission.member_read,AdminPermission.moderation_read,AdminPermission.support_read);
  return Set.of();
 }
 public void require(Long id,AdminPermission permission) {if(!permissions(id).contains(permission)) AccessSupport.denied();}
 public AdminPermission route(String method,String path) {
  boolean read="GET".equals(method);
  if(path.equals("/admin/me")) return null;
  if(path.equals("/admin/reauth")) return AdminPermission.staff_manage;
  if(path.startsWith("/admin/staff")) return read?AdminPermission.staff_read:AdminPermission.staff_manage;
  if(path.startsWith("/admin/audit-events")) return AdminPermission.audit_read;
  if(path.startsWith("/admin/job-verifications")) return AdminPermission.job_review;
  if(path.startsWith("/admin/members")) return read?AdminPermission.member_read:AdminPermission.member_manage;
  if(path.startsWith("/admin/reports")) return path.endsWith("/audits")?AdminPermission.audit_read:read?AdminPermission.moderation_read:AdminPermission.moderation;
  if(path.startsWith("/admin/support-tickets")) return read?AdminPermission.support_read:AdminPermission.support_reply;
  if(path.startsWith("/admin/billing")) return AdminPermission.billing_adjust;
  AccessSupport.denied();return null;
 }
}
