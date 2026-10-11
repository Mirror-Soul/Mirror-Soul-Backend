package com.mirrorsoul.mirrorsoul_api.quality;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.*;
import com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class TrainingReadService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TrainingLifecycleRepository events;
    private final TrainingRetryRepository retries;
    @Value("${quality.max-training-retries:3}") private int maxRetries;
    private static final String SOURCES = """
        SELECT CONCAT('VOICE-',j.id) training_job_id,'VOICE' kind,j.id source_id,j.user_id,c.id clone_id,
          COALESCE(j.result_clone_version,j.base_clone_version,0) clone_version,
          CASE WHEN j.status='PROCESSING' AND j.error_retryable=TRUE THEN 'FAILED' ELSE j.status END status,
          CASE WHEN j.error_retryable IS NOT NULL AND j.error_code IS NULL THEN 'TRAINING_FAILED'
            WHEN j.status='FAILED' AND j.error_code IS NULL AND j.sqs_message_id IS NULL THEN 'DISPATCH_FAILED' ELSE j.error_code END failure_code,
          COALESCE(j.error_retryable,j.status='FAILED' AND j.sqs_message_id IS NULL) retry_allowed,
          j.retry_count,CASE WHEN j.retried_from_job_id IS NULL THEN NULL ELSE CONCAT('VOICE-',j.retried_from_job_id) END retried_from_job_id,
          j.created_at,j.started_at,j.finished_at completed_at,j.updated_at
        FROM voice_training_jobs j JOIN clones c ON c.user_id=j.user_id
        UNION ALL
        SELECT CONCAT('FACE-',j.id),'FACE',j.id,j.user_id,c.id,
          COALESCE(j.result_clone_version,j.base_clone_version,0),
          CASE WHEN j.status='PROCESSING' AND j.error_retryable=TRUE THEN 'FAILED' ELSE j.status END,
          CASE WHEN j.error_retryable IS NOT NULL AND j.error_code IS NULL THEN 'TRAINING_FAILED'
            WHEN j.status='FAILED' AND j.error_code IS NULL AND j.sqs_message_id IS NULL THEN 'DISPATCH_FAILED' ELSE j.error_code END,
          COALESCE(j.error_retryable,j.status='FAILED' AND j.sqs_message_id IS NULL),j.retry_count,
          CASE WHEN j.retried_from_job_id IS NULL THEN NULL ELSE CONCAT('FACE-',j.retried_from_job_id) END,j.created_at,j.started_at,j.finished_at,j.updated_at
        FROM face_training_jobs j JOIN clones c ON c.user_id=j.user_id
        UNION ALL
        SELECT CONCAT('PROFILE-',j.id),'PROFILE',j.id,c.user_id,c.id,
          COALESCE(j.result_clone_version,j.base_clone_version,0),j.status,j.error_code,
          COALESCE(j.retry_allowed,COALESCE(j.error_code,'') NOT IN ('INVALID_GENERATED_PROFILE','GEMINI_HTTP_400','GEMINI_HTTP_401','GEMINI_HTTP_403')),
          j.retry_count,CASE WHEN j.retried_from_job_id IS NULL THEN NULL ELSE CONCAT('PROFILE-',j.retried_from_job_id) END,
          j.created_at,j.started_at,j.completed_at,j.updated_at
        FROM clone_profile_generation_jobs j JOIN clones c ON c.id=j.clone_id
        UNION ALL
        SELECT CONCAT('RAG-',j.clone_id,'-',j.requested_revision),'RAG',j.clone_id,c.user_id,c.id,
          COALESCE(j.result_clone_version,j.base_clone_version,0),
          COALESCE(j.training_status,CASE WHEN j.last_error IS NOT NULL THEN 'FAILED' WHEN j.lease_until IS NOT NULL THEN 'PROCESSING'
            WHEN j.requested_revision>j.delivered_revision THEN 'PENDING' ELSE 'PROCESSING' END),
          CASE WHEN j.training_status='FAILED' THEN j.training_failure_code WHEN j.training_status IS NULL AND j.last_error IS NOT NULL
            THEN 'PROVIDER_UNAVAILABLE' ELSE NULL END,
          CASE WHEN j.training_status='FAILED' THEN j.training_retry_allowed WHEN j.training_status IS NULL AND j.last_error IS NOT NULL
            THEN TRUE ELSE FALSE END,j.retry_count,NULL,j.created_at,j.started_at,
          COALESCE(j.training_finished_at,CASE WHEN j.training_status IS NULL AND j.last_error IS NOT NULL THEN j.finished_at ELSE NULL END),j.updated_at
        FROM rag_profile_jobs j JOIN clones c ON c.id=j.clone_id
        """;
    public long countStalled(LocalDateTime cutoff) {
        return count(new TrainingJobFilter(null,null,null,AdminListWindow.all(),false,cutoff));
    }
    public long countUnresolvedFailures() {
        return count(new TrainingJobFilter(null,null,"FAILED",AdminListWindow.all(),true,null));
    }
    public Page<Job> list(TrainingId.Kind kind,Long memberId,String status,int page,int size) {
        return list(new TrainingJobFilter(kind,memberId,status,AdminListWindow.all(),false,null),page,size);
    }
    private record QueryFilter(String where,Map<String,Object> args) {}
    private QueryFilter filter(TrainingJobFilter f) {
        String filter=" WHERE 1=1"; Map<String,Object> args=new HashMap<>();
        if(f.kind()!=null){filter+=" AND kind=:kind";args.put("kind",f.kind().name());}
        if(f.memberId()!=null){filter+=" AND user_id=:member";args.put("member",f.memberId());}
        if(f.status()!=null){filter+=" AND status=:status";args.put("status",f.status());}
        if(f.window().from()!=null){filter+=" AND created_at>=:from";args.put("from",AdminListWindow.local(f.window().from()));}
        if(f.window().to()!=null){filter+=" AND created_at<:to";args.put("to",AdminListWindow.local(f.window().to()));}
        if(f.unresolvedOnly())filter+=" AND NOT EXISTS(SELECT 1 FROM training_retry_commands r WHERE r.source_job_id=jobs.training_job_id)";
        if(f.stalledBefore()!=null){
            filter+=" AND status IN ('PENDING','PROCESSING') AND GREATEST(COALESCE("
                    +"(SELECT MAX(e.created_at) FROM training_lifecycle_events e WHERE e.training_job_id=jobs.training_job_id AND e.accepted=TRUE),updated_at,created_at),"
                    +"COALESCE(updated_at,created_at))<:cutoff";
            args.put("cutoff",f.stalledBefore());
        }
        return new QueryFilter(filter,args);
    }
    public long count(TrainingJobFilter f) {
        var query=filter(f);
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM ("+SOURCES+") jobs"+query.where(),query.args(),Long.class));
    }
    public Page<Job> list(TrainingJobFilter f,int page,int size) {
        if(page<0||size<1||size>100)throw QualitySupport.invalid();
        var query=filter(f);var args=query.args();
        long total=count(f);
        args.put("limit",size);args.put("offset",Math.multiplyExact((long)page,size));
        var rows=jdbc.query("SELECT * FROM ("+SOURCES+") jobs"+query.where()+" ORDER BY created_at DESC,training_job_id DESC LIMIT :limit OFFSET :offset",args,this::map);
        return new PageImpl<>(rows,PageRequest.of(page,size),total);
    }
    public Job get(String id) {
        TrainingId.parse(id);
        var rows=jdbc.query("SELECT * FROM ("+SOURCES+") jobs WHERE training_job_id=:id",Map.of("id",id),this::map);
        if(rows.isEmpty())throw QualitySupport.missing();
        return rows.get(0);
    }
    private LocalDateTime time(ResultSet r,String name) throws SQLException {
        var t=r.getTimestamp(name);return t==null?null:t.toLocalDateTime();
    }
    private Job map(ResultSet r,int index) throws SQLException {
        String id=r.getString("training_job_id"),status=r.getString("status");
        var failure=QualitySupport.safeFailure(r.getString("failure_code"));
        Stage stage="COMPLETED".equals(status)?Stage.COMPLETED:failure==QualitySupport.Failure.DISPATCH_FAILED
                ?Stage.DISPATCHING:"PENDING".equals(status)?Stage.QUEUED:Stage.TRAINING;
        var event=events.findFirstByTrainingJobIdAndAcceptedTrueOrderBySequenceDesc(id);
        var updated=time(r,"updated_at");
        var started=events.findFirstByTrainingJobIdAndAcceptedTrueAndStateOrderBySequenceAsc(id,State.STARTED)
                .map(TrainingLifecycleEvent::getOccurredAt).orElse(time(r,"started_at"));
        var completed=time(r,"completed_at");
        if(event.isPresent()){
            var e=event.get();
            if(!"COMPLETED".equals(status))stage=e.getStage();
            if(updated==null||updated.isBefore(e.getCreatedAt()))updated=e.getCreatedAt();
            if(e.getState()==State.COMPLETED&&"COMPLETED".equals(status)||e.getState()==State.FAILED&&"FAILED".equals(status))
                completed=e.getOccurredAt();
        }
        int count=r.getInt("retry_count");
        return new Job(id,TrainingId.Kind.valueOf(r.getString("kind")),r.getLong("source_id"),r.getLong("user_id"),
                r.getLong("clone_id"),r.getLong("clone_version"),QualitySupport.correlation(id),stage,status,failure,
                "FAILED".equals(status)&&r.getBoolean("retry_allowed")&&count<maxRetries&&!retries.existsBySourceJobId(id),count,
                r.getString("retried_from_job_id"),time(r,"created_at"),started,completed,updated);
    }
}
