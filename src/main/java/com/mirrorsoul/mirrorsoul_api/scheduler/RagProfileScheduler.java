package com.mirrorsoul.mirrorsoul_api.scheduler;

import com.mirrorsoul.mirrorsoul_api.repository.RagProfileJobRepository;
import com.mirrorsoul.mirrorsoul_api.service.RagProfileClient;
import com.mirrorsoul.mirrorsoul_api.service.RagProfileJobService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.profile", name = "enabled", havingValue = "true")
public class RagProfileScheduler {
    private final RagProfileJobRepository jobs;
    private final RagProfileJobService service;
    private final RagProfileClient client;

    @Scheduled(scheduler = "ragProfileTaskScheduler", fixedDelayString = "${rag.profile.poll-ms:10000}",
            initialDelayString = "${rag.profile.poll-ms:10000}")
    public void publish() {
        for (Long id : jobs.findDue(LocalDateTime.now(), PageRequest.of(0, 10))) {
            try {
                var claim = service.claim(id);
                if (claim == null) continue;
                String error = null;
                try {
                    client.send(claim.request()); // No DB transaction/clone lock while AI calls back.
                } catch (RuntimeException exception) {
                    // Error responses can contain personal data; record only the exception type.
                    error = exception.getClass().getSimpleName();
                    log.warn("RAG profile delivery failed. cloneId={}, error={}", id, error);
                }
                service.finish(claim, error);
            } catch (RuntimeException exception) {
                log.error("RAG profile worker failed. cloneId={}, error={}", id, exception.getClass().getSimpleName());
            }
        }
    }
}
