package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.domain.VideoCall;
import com.mirrorsoul.mirrorsoul_api.domain.enums.CallMediaType;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyCallDiagnosticRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.mirrorsoul.mirrorsoul_api.quality.CallQualityDtos.*;
import com.mirrorsoul.mirrorsoul_api.quality.CallQualityDtos.Window;

@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CallDiagnosticsReadService {
    private final VideoCallRepository calls;
    private final CallQualityEventRepository events;
    private final CallStageMeasurementRepository samples;
    private final TalkTimeTransactionRepository ledger;
    private final SafetyCallDiagnosticRepository safety;
    private final JdbcTemplate jdbc;

    public static List<String> trainingIds(VideoCall call) {
        List<String> ids=new ArrayList<>();
        if(call.getVoiceTrainingJobId()!=null)ids.add("VOICE-"+call.getVoiceTrainingJobId());
        if(call.getFaceTrainingJobId()!=null)ids.add("FACE-"+call.getFaceTrainingJobId());
        if(call.getPersonalityRevision()!=null&&call.getPersonalityRevision()>0)
            ids.add("RAG-"+call.getClone().getId()+"-"+call.getPersonalityRevision());
        return ids;
    }
    private OffsetDateTime offset(LocalDateTime at) { return at.atZone(ZoneId.systemDefault()).toOffsetDateTime(); }
    public Diagnostics get(Long id) {
        var call=calls.findById(id).orElseThrow(QualitySupport::missing);
        var stageRows=samples.findByCallIdOrderByMeasuredAtDescIdDesc(id,PageRequest.of(0,201));
        var measurements=stageRows.stream().limit(200)
                .map(s->new Sample(s.getTurnKey(),s.getStage(),s.getLatencyMs(),s.getMeasuredAt())).toList();
        List<Signal> errors=new ArrayList<>();
        events.errors(id,PageRequest.of(0,201)).forEach(e->errors.add(new Signal(e.getErrorCode().name(),e.getOccurredAt())));
        safety.findByCallIdOrderByIdDesc(id,PageRequest.of(0,201)).forEach(e->errors.add(new Signal(e.getCode().name(),e.getCreatedAt())));
        errors.sort(Comparator.comparing(Signal::occurredAt).reversed());
        var link=events.findFirstByCallIdAndStepOrderByOccurredAtDescIdDesc(id,Step.CONNECTION).map(CallQualityEvent::link).orElse(null);
        var refs=ledger.findByVideoCallId(id).stream()
                .map(e->new LedgerRef(e.getId(),e.getReason().name(),e.getDeltaSeconds(),e.getBalanceAfterSeconds())).toList();
        var stats=turnStatistics(id,call.getMediaType());
        var to=offset(LocalDateTime.now().plusSeconds(5));
        return new Diagnostics(id,call.getUser().getId(),call.getClone().getId(),call.getCloneVersion(),
                UUID.fromString(call.diagnosticCorrelationId()),trainingIds(call),call.getMediaType(),call.getStatus(),
                call.getBillingEndReason(),call.getBillingEndSource(),call.getStartedAt(),call.getConnectedAt(),call.getEndedAt(),
                link,errors.stream().limit(200).toList(),refs,events.countByCallId(id),stats[0],stats[1],measurements,stageRows.size()>200,errors.size()>200,
                distribution(offset(call.getStartedAt().minusSeconds(5)),to,id,call.getClone().getId(),call.getCloneVersion(),call.getMediaType()));
    }
    private long[] turnStatistics(Long id,CallMediaType type) {
        String grouped="""
            SELECT turn_key,
              MIN(CASE WHEN step='STT_ENDED' THEN occurred_at END) stt,
              MIN(CASE WHEN step='LLM_FIRST_TOKEN' THEN occurred_at END) llm,
              MIN(CASE WHEN step='TTS_FIRST_AUDIO' THEN occurred_at END) tts,
              MIN(CASE WHEN step='VIDEO_READY' THEN occurred_at END) video,
              MIN(CASE WHEN step='CLIENT_DELIVERED' THEN occurred_at END) delivered
            FROM call_quality_events WHERE call_id=? AND step NOT IN ('CONNECTION','ERROR') GROUP BY turn_key
            """;
        String extra=type==CallMediaType.VIDEO?" + CASE WHEN video<tts THEN 1 ELSE 0 END + CASE WHEN delivered<video THEN 1 ELSE 0 END"
                :" + CASE WHEN delivered<tts THEN 1 ELSE 0 END";
        String missing=type==CallMediaType.VIDEO?" OR video IS NULL":"";
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN llm<stt THEN 1 ELSE 0 END + CASE WHEN tts<llm THEN 1 ELSE 0 END"
                        +" + CASE WHEN delivered<stt THEN 1 ELSE 0 END"+extra
                        +"),0),COALESCE(SUM(CASE WHEN stt IS NULL OR llm IS NULL OR tts IS NULL OR delivered IS NULL"
                        +missing+" THEN 1 ELSE 0 END),0) FROM ("+grouped+") turns",
                (r,n)->new long[]{r.getLong(1),r.getLong(2)},id);
    }
    public Window distribution(OffsetDateTime from,OffsetDateTime to,Long callId,Long cloneId,Long cloneVersion,CallMediaType type) {
        if(from==null||to==null||!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(31))>0&&callId==null)throw QualitySupport.invalid();
        var start=QualitySupport.local(from);var end=QualitySupport.local(to);
        List<Percentiles> output=new ArrayList<>();
        for(Stage stage:Stage.values()){
            Specification<CallStageMeasurement> filter=(r,q,cb)->{
                List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();
                p.add(cb.equal(r.get("stage"),stage));p.add(cb.greaterThanOrEqualTo(r.get("measuredAt"),start));
                p.add(cb.lessThan(r.get("measuredAt"),end));
                if(callId!=null)p.add(cb.equal(r.get("call").get("id"),callId));
                if(cloneId!=null)p.add(cb.equal(r.get("call").get("clone").get("id"),cloneId));
                if(cloneVersion!=null)p.add(cb.equal(r.get("call").get("cloneVersion"),cloneVersion));
                if(type!=null)p.add(cb.equal(r.get("call").get("mediaType"),type));
                return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
            };
            long count=samples.count(filter);
            output.add(new Percentiles(stage,count,percentile(filter,count,50),percentile(filter,count,95)));
        }
        return new Window(callId==null?"AGGREGATE":"CALL",from,to,callId,cloneId,cloneVersion,type,output);
    }
    private Long percentile(Specification<CallStageMeasurement> filter,long count,int percentile) {
        if(count==0)return null;
        int rank=Math.toIntExact((count*percentile+99)/100-1);
        return samples.findAll(filter,PageRequest.of(rank,1,Sort.by("latencyMs").ascending().and(Sort.by("id"))))
                .getContent().get(0).getLatencyMs();
    }
}
