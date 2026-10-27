package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
@Component @RequiredArgsConstructor
public class SupportAccess {
 private final UserRepository users;
 public User user(Long id) {return users.findById(id).orElseThrow(()->new GeneralException(GeneralErrorCode.USER_NOT_FOUND));}
 public User reader(Long id) {User u=user(id);if(u.getStatus()!=UserStatus.ACTIVE||!Set.of(UserRole.ADMIN,UserRole.ADMIN_VIEWER,UserRole.SUPER_ADMIN).contains(u.getRole())) forbidden();return u;}
 public User operator(Long id) {User u=reader(id);if(u.getRole()==UserRole.ADMIN_VIEWER) forbidden();return u;}
 public void owner(Long id,SupportTicket t) {if(!Objects.equals(t.getMemberId(),id)) forbidden();}
 public void writer(Long id,SupportTicket t) {User u=operator(id);if(u.getRole()!=UserRole.SUPER_ADMIN&&!Objects.equals(t.getAssigneeId(),id)) forbidden();}
 public void attachment(Long actor,SupportTicket ticket) {if(!Objects.equals(actor,ticket.getMemberId())) writer(actor,ticket);}
 static void forbidden() {throw new GeneralException(GeneralErrorCode.FORBIDDEN);}
 static GeneralException conflict() {return new GeneralException(GeneralErrorCode.SUPPORT_CONFLICT);}
 static GeneralException missing() {return new GeneralException(GeneralErrorCode.SUPPORT_NOT_FOUND);}
 static void version(SupportTicket ticket,Long version) {if(!Objects.equals(ticket.getVersion(),version)) throw conflict();}
 static LocalDateTime now() {return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);}
 static Pageable page(int page,int size) {if(page<0||size<1||size>100) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));}
 static String key(String value) {return value.toLowerCase(Locale.ROOT);}
 static String hash(Object... values) {
  try {MessageDigest d=MessageDigest.getInstance("SHA-256");for(Object v:values) {byte[] b=String.valueOf(v).getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}
  catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
 }
}
