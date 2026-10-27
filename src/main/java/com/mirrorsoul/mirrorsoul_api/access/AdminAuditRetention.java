package com.mirrorsoul.mirrorsoul_api.access;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
@Slf4j @Component
public class AdminAuditRetention {
 private final AdminAuditEventRepository events;
 private final int days;
 private final AdminReauthenticationRepository reauths;
 public AdminAuditRetention(AdminAuditEventRepository events,AdminReauthenticationRepository reauths,@Value("${security.admin.audit-retention-days:365}") int days) {if(days<90||days>3650) throw new IllegalArgumentException("Audit retention must be 90..3650 days");this.events=events;this.days=days;this.reauths=reauths;}
 @Scheduled(cron="${security.admin.audit-cleanup-cron:0 0 4 * * *}") @Transactional public void purge() {
  try {events.purge(AccessSupport.now().minusDays(days));reauths.purge(AccessSupport.now().minusDays(1));}
  catch(RuntimeException e) {log.error("Admin audit cleanup failed exceptionType={}",e.getClass().getSimpleName());throw e;}
 }
}
