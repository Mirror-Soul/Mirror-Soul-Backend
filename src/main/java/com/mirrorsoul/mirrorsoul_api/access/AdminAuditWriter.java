package com.mirrorsoul.mirrorsoul_api.access;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Slf4j @Service @RequiredArgsConstructor
public class AdminAuditWriter {
 private final AdminAuditEventRepository events;
 @Transactional(propagation=Propagation.MANDATORY) public void enlisted(Long actor,String action,String target,String result,String reason,String correlation) {store(actor,action,target,result,reason,correlation);}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void isolated(Long actor,String action,String target,String result,String reason,String correlation) {store(actor,action,target,result,reason,correlation);}
 private void store(Long actor,String action,String target,String result,String reason,String correlation) {
  AdminAuditEvent event;
  try {event=events.saveAndFlush(new AdminAuditEvent(actor,action,target,result,reason,correlation,AccessSupport.now()));}
  catch(RuntimeException e) {log.error("Admin audit storage failed correlationId={} exceptionType={}",correlation,e.getClass().getSimpleName());throw e;}
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
   @Override public void afterCommit() {log.info("Admin audit committed eventId={} staffId={} action={} target={} result={} reasonCode={} correlationId={}",event.getId(),actor,action,target,result,reason,correlation);}
  });
  var ctx=AdminAuditContext.get();if(ctx!=null) ctx.recordedResult=result;
 }
}
