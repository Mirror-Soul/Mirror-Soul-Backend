package com.mirrorsoul.mirrorsoul_api.support;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import io.micrometer.core.instrument.MeterRegistry;
@Slf4j @Component @RequiredArgsConstructor
public class SupportAttachmentCleanup {
 private final SupportAttachmentRepository repository;
 private final SupportAttachmentService attachments;
 private final MeterRegistry metrics;
 @Scheduled(fixedDelayString="${support.attachments.cleanup-interval-ms:3600000}") public void run() {
  for(Long id:repository.expired(SupportAccess.now(),PageRequest.of(0,100))) try {attachments.deleteExpired(id);}
  catch(RuntimeException e) {metrics.counter("support.attachments.cleanup.failed").increment();log.error("Support attachment cleanup failed attachmentId={} exceptionType={}",id,e.getClass().getSimpleName());}
 }
}
