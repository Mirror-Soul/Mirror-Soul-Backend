package com.mirrorsoul.mirrorsoul_api.jobreview;

import com.mirrorsoul.mirrorsoul_api.repository.JobVerificationRequestRepository;
import com.mirrorsoul.mirrorsoul_api.repository.PushDeviceRepository;
import com.mirrorsoul.mirrorsoul_api.service.FileService;
import com.mirrorsoul.mirrorsoul_api.service.PushNotificationService;
import com.mirrorsoul.mirrorsoul_api.event.JobVerificationReviewedEvent;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class JobReviewLifecycleWorker {
    private final JobVerificationRequestRepository requests;
    private final FileService files;
    private final ObjectProvider<PushNotificationService> pushes;
    private final PushDeviceRepository devices;
    private final TransactionTemplate tx;
    public JobReviewLifecycleWorker(JobVerificationRequestRepository requests, FileService files,
            ObjectProvider<PushNotificationService> pushes, PushDeviceRepository devices, PlatformTransactionManager manager) {
        this.requests=requests; this.files=files; this.pushes=pushes; this.devices=devices;
        tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString="${job-verification.evidence.cleanup-delay-ms:3600000}")
    public void cleanup() {
        try {
            for (Long id: requests.expiredEvidence(LocalDateTime.now(),PageRequest.of(0,100))) {
                try { delete(id); } catch (RuntimeException e) { log.error("Job evidence cleanup failed requestId={} exceptionType={}",id,e.getClass().getSimpleName()); }
            }
        } catch (RuntimeException e) { log.error("Job evidence cleanup failed exceptionType={}",e.getClass().getSimpleName()); }
    }
    public void delete(Long id) {
        tx.executeWithoutResult(status -> {
            var r=requests.findByIdForUpdate(id).orElse(null);
            if(r==null || r.getEvidenceDeletedAt()!=null || r.getEvidenceExpiresAt().isAfter(LocalDateTime.now())) return;
            try {
                for(var file:r.getFiles()) files.deleteJobCertificationSnapshot(r.getUser().getUuid(),
                        new FileService.VerifiedJobCertificationImage(file.getBucket(),file.getObjectKey(),file.getObjectVersionId(),file.getObjectEtag()));
                r.evidenceDeletion(true,LocalDateTime.now());
            } catch(RuntimeException e) {
                r.evidenceDeletion(false,LocalDateTime.now());
                log.error("Job evidence cleanup failed requestId={} exceptionType={}",id,e.getClass().getSimpleName());
            }
        });
    }
    @Scheduled(fixedDelayString="${job-verification.notification.poll-delay-ms:60000}")
    public void notifications() {
        try {
            for(Long id:requests.notificationQueue(LocalDateTime.now().minusMinutes(10),PageRequest.of(0,100))) {
                try { deliver(id); } catch(RuntimeException e) { log.error("Job notification worker failed requestId={} exceptionType={}",id,e.getClass().getSimpleName()); }
            }
        } catch(RuntimeException e) { log.error("Job notification worker failed exceptionType={}",e.getClass().getSimpleName()); }
    }
    private record Claim(JobVerificationReviewedEvent event, int attempt) {}
    public void deliver(Long id) {
        Claim claim=tx.execute(status -> {
            var r=requests.findByIdForUpdate(id).orElse(null);
            if(r==null) return null;
            if("SENDING".equals(r.getNotificationState())) {
                if(r.getNotificationUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(10)))
                    r.notification("UNKNOWN","DELIVERY_INTERRUPTED",LocalDateTime.now());
                return null;
            }
            if(!"PENDING".equals(r.getNotificationState())) return null;
            if(pushes.getIfAvailable()==null) { r.notification("DISABLED","PUSH_DISABLED",LocalDateTime.now()); return null; }
            if(devices.findAllByUserUuidInAndEnabledTrue(java.util.List.of(r.getUser().getUuid())).isEmpty()) {
                r.notification("NO_DEVICE","NO_ACTIVE_DEVICE",LocalDateTime.now()); return null;
            }
            r.notification("SENDING",null,LocalDateTime.now());
            return new Claim(new JobVerificationReviewedEvent(id,r.getUser().getUuid(),r.getStatus()),r.getNotificationAttempts());
        });
        if(claim==null) return;
        String result="SENT", error=null;
        try { pushes.getObject().sendJobVerificationResult(claim.event()); }
        catch(RuntimeException e) { result=e instanceof JobNotificationDeliveryException ? "FAILED" : "UNKNOWN"; error="DELIVERY_UNCERTAIN";
            log.error("Job notification delivery failed requestId={} exceptionType={}",id,e.getClass().getSimpleName()); }
        String finalResult=result, finalError=error;
        tx.executeWithoutResult(status -> {
            var r=requests.findByIdForUpdate(id).orElse(null);
            if(r!=null && r.getNotificationAttempts()==claim.attempt() && "SENDING".equals(r.getNotificationState())) r.notification(finalResult,finalError,LocalDateTime.now());
        });
    }
}
