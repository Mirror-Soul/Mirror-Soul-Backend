package com.mirrorsoul.mirrorsoul_api.billing;

import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import com.mirrorsoul.mirrorsoul_api.service.CallService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class CallBillingExpiry {
    private final VideoCallRepository calls;
    private final CallService service;
    @Scheduled(fixedDelayString = "${billing.call-expiry-interval-ms:5000}")
    public void expire() {
        for (Long id : calls.findBillingExpired(LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 100))) {
            try { service.endExpired(id); }
            catch (RuntimeException e) { log.error("Call billing expiry failed callId={} exceptionType={}", id, e.getClass().getSimpleName()); }
        }
    }
}
