package com.mirrorsoul.mirrorsoul_api.access;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class AdminReauthLimiter {
 private final UserRepository users;
 private final AdminAuditEventRepository events;
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void attempt(Long actor) {
  users.findByIdForUpdate(actor).orElseThrow(()->new GeneralException(GeneralErrorCode.FORBIDDEN));
  if(events.countByStaffIdAndActionAndOccurredAtAfter(actor,"REAUTH_ATTEMPT",AccessSupport.now().minusMinutes(15))>=5) throw new GeneralException(GeneralErrorCode.STAFF_RATE_LIMITED);
  events.saveAndFlush(new AdminAuditEvent(actor,"REAUTH_ATTEMPT","staff:"+actor,"ATTEMPT","PASSWORD_CHECK",AdminAuditContext.get()==null?UUID.randomUUID().toString():AdminAuditContext.get().correlationId,AccessSupport.now()));
 }
}
