package com.mirrorsoul.mirrorsoul_api.access;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.ArrayList;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminAuditService {
 private final AdminPermissionService permissions;
 private final AdminAuditEventRepository events;
 public Page<AccessDtos.AuditView> list(Long actor,Long staffId,String result,LocalDateTime from,LocalDateTime to,int page,int size) {
  permissions.require(actor,AdminPermission.audit_read);
  if(page<0||size<1||size>100||(from!=null&&to!=null&&from.isAfter(to))) throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.INVALID_PARAMETER);
  return events.findAll((r,q,b)-> {var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();if(staffId!=null) predicates.add(b.equal(r.get("staffId"),staffId));if(result!=null) predicates.add(b.equal(r.get("result"),result));if(from!=null) predicates.add(b.greaterThanOrEqualTo(r.get("occurredAt"),from));if(to!=null) predicates.add(b.lessThanOrEqualTo(r.get("occurredAt"),to));return b.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));},PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))).map(e->new AccessDtos.AuditView(e.getId(),e.getStaffId(),e.getAction(),e.getTarget(),e.getOccurredAt(),e.getResult(),e.getReasonCode(),e.getCorrelationId()));
 }
}
