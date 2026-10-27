package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.repository.VideoCallRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.quality.CallQualityDtos.*;

@Service @RequiredArgsConstructor @Slf4j
public class CallQualityService {
    private final VideoCallRepository calls;
    private final CallQualityEventRepository events;
    private final CallStageMeasurementRepository samples;

    @Transactional
    public Received receive(Long callId,Event request) {
        VideoCall call=calls.findByIdForUpdate(callId).orElseThrow(QualitySupport::missing);
        if(!call.getClone().getId().equals(request.cloneId())||!Objects.equals(call.getCloneVersion(),request.cloneVersion())
                ||!call.diagnosticCorrelationId().equals(request.correlationId().toString()))throw QualitySupport.conflict();
        var at=QualitySupport.local(request.occurredAt());
        if(at.isBefore(call.getStartedAt().minusSeconds(5))||at.isAfter(LocalDateTime.now().plusSeconds(5))
                ||call.getEndedAt()!=null&&at.isAfter(call.getEndedAt().plusSeconds(5)))throw QualitySupport.invalid();
        if((request.step()==Step.CONNECTION)!=(request.connection()!=null)
                ||(request.step()==Step.ERROR)!=(request.errorCode()!=null))throw QualitySupport.invalid();
        if(request.step()==Step.VIDEO_READY&&call.getMediaType()!=CallMediaType.VIDEO)throw QualitySupport.invalid();
        String key=QualitySupport.hash(callId+":"+request.eventId());
        String fingerprint=QualitySupport.hash(request.turnId()+":"+request.step()+":"+request.occurredAt().toInstant()
                +":"+request.connection()+":"+request.errorCode());
        var old=events.findByEventKey(key);
        if(old.isPresent()){
            if(!old.get().getRequestHash().equals(fingerprint))throw QualitySupport.conflict();
            return new Received(callId,true);
        }
        String turn=QualitySupport.hash(request.turnId());
        if(request.step()==Step.CONNECTION||request.step()==Step.ERROR){
            events.saveAndFlush(new CallQualityEvent(call,key,fingerprint,request));
            return new Received(callId,false);
        }
        var prior=events.anchors(callId,turn);
        var anchor=prior.stream().filter(e->e.getStep()==request.step()).findFirst();
        if(anchor.isPresent()){
            if(!anchor.get().getRequestHash().equals(fingerprint))throw QualitySupport.conflict();
            events.saveAndFlush(new CallQualityEvent(call,key,fingerprint,request));
            return new Received(callId,true);
        }
        var stored=events.saveAndFlush(new CallQualityEvent(call,key,fingerprint,request));
        prior.add(stored);
        var anchors=new EnumMap<Step,LocalDateTime>(Step.class);
        prior.forEach(e->anchors.putIfAbsent(e.getStep(),e.getOccurredAt()));
        measure(call,turn,anchors,Stage.STT_TO_LLM,Step.STT_ENDED,Step.LLM_FIRST_TOKEN);
        measure(call,turn,anchors,Stage.LLM_TO_TTS,Step.LLM_FIRST_TOKEN,Step.TTS_FIRST_AUDIO);
        if(call.getMediaType()==CallMediaType.VIDEO){
            measure(call,turn,anchors,Stage.TTS_TO_VIDEO,Step.TTS_FIRST_AUDIO,Step.VIDEO_READY);
            measure(call,turn,anchors,Stage.VIDEO_TO_CLIENT,Step.VIDEO_READY,Step.CLIENT_DELIVERED);
        }else measure(call,turn,anchors,Stage.TTS_TO_CLIENT,Step.TTS_FIRST_AUDIO,Step.CLIENT_DELIVERED);
        measure(call,turn,anchors,Stage.STT_TO_CLIENT,Step.STT_ENDED,Step.CLIENT_DELIVERED);
        return new Received(callId,false);
    }
    private void measure(VideoCall call,String turn,Map<Step,LocalDateTime> points,Stage stage,Step from,Step to) {
        var start=points.get(from);var end=points.get(to);
        if(start==null||end==null||end.isBefore(start)||samples.existsByCallIdAndTurnKeyAndStage(call.getId(),turn,stage))return;
        long ms=Duration.between(start,end).toMillis();
        samples.saveAndFlush(new CallStageMeasurement(call,turn,stage,ms,end));
        QualitySupport.afterCommit(()->log.info("Call stage measured callId={} cloneId={} cloneVersion={} correlationId={} mediaType={} stage={} latencyMs={}",
                call.getId(),call.getClone().getId(),call.getCloneVersion(),call.diagnosticCorrelationId(),call.getMediaType(),stage,ms));
    }
}
