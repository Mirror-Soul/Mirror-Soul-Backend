package com.mirrorsoul.mirrorsoul_api.quality;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.service.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="quality.training-dispatch-enabled",havingValue="true")
public class TrainingDispatchRecovery {
    private final VoiceTrainingJobRepository voices;
    private final FaceTrainingJobRepository faces;
    private final VoiceTrainingJobPublisher voicePublisher;
    private final FaceTrainingJobPublisher facePublisher;
    @Scheduled(fixedDelayString="${quality.dispatch-recovery-ms:30000}",initialDelayString="${quality.dispatch-recovery-ms:30000}")
    public void run() {
        var cutoff=LocalDateTime.now().minusSeconds(30);var page=PageRequest.of(0,20);
        voices.findUndispatched(cutoff,page).forEach(id->send(()->voicePublisher.publish(id),"VOICE-"+id));
        faces.findUndispatched(cutoff,page).forEach(id->send(()->facePublisher.publish(id),"FACE-"+id));
    }
    private void send(Runnable action,String id) {
        try{action.run();}
        catch(RuntimeException e){log.error("Training dispatch recovery failed trainingJobId={} failureCode=DISPATCH_FAILED",id);}
    }
}
