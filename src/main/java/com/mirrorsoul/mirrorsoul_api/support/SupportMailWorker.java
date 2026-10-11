package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.Delivery;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import jakarta.mail.internet.MimeMessage;
@Slf4j @Component
public class SupportMailWorker {
 private final SupportEntryRepository entries;
 private final SupportDeliveryService deliveries;
 private final JavaMailSender mail;
 private final MeterRegistry metrics;
 private final boolean enabled;
 private final String from;
 public SupportMailWorker(SupportEntryRepository entries,SupportDeliveryService deliveries,JavaMailSender mail,MeterRegistry metrics,
   @Value("${support.mail.enabled:false}") boolean enabled,@Value("${support.mail.from:}") String from) {
  this.entries=entries;this.deliveries=deliveries;this.mail=mail;this.metrics=metrics;this.enabled=enabled;this.from=from;
 }
 @Scheduled(fixedDelayString="${support.mail.interval-ms:30000}") public void run() {
  int recovered=deliveries.recover();if(recovered>0) {metrics.counter("support.mail.uncertain").increment(recovered);log.error("Support mail uncertain deliveries count={}",recovered);}
  for(Long id:entries.pending(Delivery.PENDING,PageRequest.of(0,50))) dispatch(id);
 }
 public void dispatch(Long id) {
  var a=deliveries.claim(id,enabled&&!from.isBlank());
  if(a==null) {
   var e=entries.findById(id).orElse(null);if(e!=null&&e.getDelivery()==Delivery.FAILED) {metrics.counter("support.mail.failed").increment();log.error("Support mail failed entryId={} code={}",id,e.getFailureCode());}return;
  }
  try {
   MimeMessage m=mail.createMimeMessage();m.setFrom(from);m.setRecipients(jakarta.mail.Message.RecipientType.TO,a.email());
   m.setSubject("[MirrorSoul] 고객문의 #"+a.ticketId()+" 답변","UTF-8");m.setText(a.body(),"UTF-8");
   m.saveChanges();m.setHeader("Message-ID","<support-"+a.entryId()+"@mirrorsoul.support>");
   mail.send(m);
   deliveries.finish(a,Delivery.SENT,null);metrics.counter("support.mail.sent").increment();
  } catch(org.springframework.mail.MailAuthenticationException e) {
   deliveries.finish(a,Delivery.FAILED,"MAIL_AUTH_FAILED");metrics.counter("support.mail.failed").increment();log.error("Support mail failed entryId={} code=MAIL_AUTH_FAILED",id);
  } catch(Exception e) {
   // SMTP has no idempotent acceptance API. Never automatically resend ambiguous delivery.
   deliveries.finish(a,Delivery.UNKNOWN,"DELIVERY_UNCERTAIN");metrics.counter("support.mail.uncertain").increment();log.error("Support mail uncertain entryId={} exceptionType={}",id,e.getClass().getSimpleName());
  }
 }
}
