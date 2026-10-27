package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class SupportDeliveryService {
 private final SupportEntryRepository entries;
 private final SupportTicketRepository tickets;
 private final UserRepository users;
 public record Attempt(Long entryId,Long ticketId,String token,String email,String body) {}
 @Transactional public Attempt claim(Long id,boolean enabled) {
  var e=entries.lock(id).orElseThrow(SupportAccess::missing);
  if(e.getDelivery()!=Delivery.PENDING) return null;
  var t=tickets.findById(e.getTicketId()).orElseThrow(SupportAccess::missing);
  var member=users.findById(t.getMemberId()).orElse(null);
  if(member==null||member.getStatus()==UserStatus.DELETED||member.getStatus()==UserStatus.INACTIVE||member.getStatus()==UserStatus.WITHDRAWAL_CONFIRMED) {e.finish(Delivery.CANCELLED,"RECIPIENT_UNAVAILABLE");return null;}
  if(!enabled) {e.finish(Delivery.FAILED,"MAIL_NOT_CONFIGURED");return null;}
  String token=UUID.randomUUID().toString();e.claim(token,SupportAccess.now());
  return new Attempt(id,t.getId(),token,member.getEmail(),e.getBody());
 }
 @Transactional public void finish(Attempt attempt,Delivery state,String code) {
  var e=entries.lock(attempt.entryId()).orElseThrow(SupportAccess::missing);
  if(e.getDelivery()==Delivery.SENDING&&Objects.equals(attempt.token(),e.getAttemptToken())) e.finish(state,code);
 }
 @Transactional public int recover() {return entries.recover(SupportAccess.now().minusMinutes(10),Delivery.UNKNOWN,Delivery.SENDING);}
}
