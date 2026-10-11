package com.mirrorsoul.mirrorsoul_api.quality;

import com.mirrorsoul.mirrorsoul_api.access.*;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.*;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.event.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.mirrorsoul.mirrorsoul_api.quality.TrainingDtos.*;

@Service @RequiredArgsConstructor @Slf4j
public class TrainingControlService {
    private final TrainingReadService reads;
    private final CloneRepository clones;
    private final VoiceTrainingJobRepository voices;
    private final FaceTrainingJobRepository faces;
    private final CloneProfileGenerationJobRepository profiles;
    private final RagProfileJobRepository rag;
    private final TrainingRetryRepository retries;
    private final TrainingLifecycleRepository events;
    private final AdminPermissionService permissions;
    private final AdminAuditWriter audit;
    private final ApplicationEventPublisher publisher;

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RetryResult retry(Long actor,String key,Retry request) {
        var id=TrainingId.parse(key);
        permissions.require(actor,AdminPermission.clone_training_retry);
        String operation=QualitySupport.hash(actor+":"+request.idempotencyKey());
        var previous=retries.findByOperationKey(operation);
        if(previous.isPresent()){
            if(!key.equals(previous.get().getSourceJobId()))throw QualitySupport.conflict();
            return previous.get().result();
        }
        var info=reads.get(key);
        var clone=clones.findLockedById(info.cloneId()).orElseThrow(QualitySupport::missing);
        previous=retries.findByOperationKey(operation);
        if(previous.isPresent()){
            if(!key.equals(previous.get().getSourceJobId()))throw QualitySupport.conflict();
            return previous.get().result();
        }
        // Source writers also take this clone lock. Re-read after it is held.
        info=reads.get(key);
        if(!info.retryAllowed()||retries.existsBySourceJobId(key))throw QualitySupport.conflict();
        if(!clone.getUser().isAiWorkAllowed(LocalDateTime.now()))
            throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(
                    com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.SAFETY_RESTRICTED);
        String next;
        switch(id.kind()){
            case VOICE -> {
                var old=voices.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
                // Retire an AI retryable failure before issuing a different immutable job ID.
                old.trainingFailure("TRAINING_FAILED",true);
                var copy=VoiceTrainingJob.create(old.getUser(),old.getSource());copy.retryOf(old);copy.traceVersion(clone.getCloneVersion());
                old.getFiles().forEach(f->copy.addFile(f.getInterviewRecord(),f.getBucket(),f.getObjectKey()));
                voices.saveAndFlush(copy);next=TrainingId.of(TrainingId.Kind.VOICE,copy.getId());
                publisher.publishEvent(new VoiceTrainingJobRequestedEvent(copy.getId()));
            }
            case FACE -> {
                var old=faces.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
                old.trainingFailure("TRAINING_FAILED",true);
                var copy=FaceTrainingJob.create(old.getUser(),old.getSource());copy.retryOf(old);copy.traceVersion(clone.getCloneVersion());
                old.getFiles().forEach(f->copy.addFile(f.getFaceFile(),f.getBucket(),f.getObjectKey()));
                faces.saveAndFlush(copy);next=TrainingId.of(TrainingId.Kind.FACE,copy.getId());
                publisher.publishEvent(new FaceTrainingJobRequestedEvent(copy.getId()));
            }
            case PROFILE -> {
                var old=profiles.findByIdForUpdate(id.sourceId()).orElseThrow(QualitySupport::missing);
                if(profiles.findFirstByCloneIdAndStatusOrderByCreatedAtAsc(clone.getId(),CloneProfileGenerationJobStatus.PENDING).isPresent()
                        ||profiles.findFirstByCloneIdAndStatusOrderByCreatedAtAsc(clone.getId(),CloneProfileGenerationJobStatus.PROCESSING).isPresent())
                    throw QualitySupport.conflict();
                var copy=CloneProfileGenerationJob.create(clone,old.getTriggerType(),old.getSourceHash(),old.getPromptVersion());
                copy.retryOf(old);profiles.saveAndFlush(copy);next=TrainingId.of(TrainingId.Kind.PROFILE,copy.getId());
            }
            case RAG -> {
                var job=rag.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
                if(job.getRequestedRevision()!=id.revision()||job.getLeaseUntil()!=null&&job.getLeaseUntil().isAfter(LocalDateTime.now()))throw QualitySupport.conflict();
                job.traceVersion(clone.getCloneVersion());job.retryRequest(LocalDateTime.now());
                next="RAG-"+id.sourceId()+"-"+job.getRequestedRevision();
            }
            default -> throw QualitySupport.invalid();
        }
        var command=new TrainingRetryCommand(operation,key,next,actor,info.retryCount()+1);
        retries.saveAndFlush(command);
        var ctx=AdminAuditContext.get();
        audit.enlisted(actor,"CLONE_TRAINING_RETRY",key,"SUCCESS","TRAINING_RETRY",
                ctx==null?UUID.randomUUID().toString():ctx.correlationId);
        String target=next;
        QualitySupport.afterCommit(()->log.info("Training retry scheduled trainingJobId={} newTrainingJobId={} correlationId={}",
                key,target,QualitySupport.correlation(target)));
        return command.result();
    }

    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Received event(String key,Lifecycle request) {
        var id=TrainingId.parse(key);
        if(id.kind()==TrainingId.Kind.RAG){
            clones.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
            var current=rag.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
            if(id.revision()<current.getRequestedRevision())return supersededRagEvent(key,id,request);
        }
        var info=reads.get(key);
        var clone=clones.findLockedById(info.cloneId()).orElseThrow(QualitySupport::missing);
        if(!Objects.equals(request.cloneId(),clone.getId())||request.cloneVersion()>clone.getCloneVersion()
                ||!QualitySupport.correlation(key).equals(request.correlationId()))throw QualitySupport.conflict();
        Long baseVersion=switch(id.kind()){
            case VOICE -> voices.findById(id.sourceId()).orElseThrow(QualitySupport::missing).getBaseCloneVersion();
            case FACE -> faces.findById(id.sourceId()).orElseThrow(QualitySupport::missing).getBaseCloneVersion();
            case PROFILE -> profiles.findById(id.sourceId()).orElseThrow(QualitySupport::missing).getBaseCloneVersion();
            case RAG -> rag.findById(id.sourceId()).orElseThrow(QualitySupport::missing).getBaseCloneVersion();
        };
        if(!Objects.equals(request.cloneVersion(),baseVersion==null?0L:baseVersion))throw QualitySupport.conflict();
        var at=QualitySupport.local(request.occurredAt());
        validateState(request);
        if(at.isAfter(LocalDateTime.now().plusSeconds(5))
                ||info.createdAt()!=null&&at.isBefore(info.createdAt().minusSeconds(5)))throw QualitySupport.invalid();
        String eventKey=QualitySupport.hash(key+":"+request.eventId());
        String fingerprint=fingerprint(request);
        var prior=events.findByEventKey(eventKey);
        if(prior.isPresent()){
            if(!prior.get().getRequestHash().equals(fingerprint))throw QualitySupport.conflict();
            QualitySupport.afterCommit(()->log.info("Training callback duplicate trainingJobId={} correlationId={}",key,request.correlationId()));
            return new Received(key,true,!prior.get().isAccepted());
        }
        assertSequence(key,request.sequence(),fingerprint);
        var latest=events.findFirstByTrainingJobIdAndAcceptedTrueOrderBySequenceDesc(key);
        if(latest.isPresent()&&latest.get().getSequence()==request.sequence()
                &&!latest.get().getRequestHash().equals(fingerprint))throw QualitySupport.conflict();
        info=reads.get(key);
        boolean ignored=latest.isPresent()&&(latest.get().getSequence()>=request.sequence()
                ||latest.get().getState()==State.COMPLETED||latest.get().getState()==State.FAILED
                ||latest.get().getStage().ordinal()>request.stage().ordinal());
        if(!ignored){
            if("COMPLETED".equals(info.status())&&request.state()!=State.COMPLETED||"STALE".equals(info.status()))ignored=true;
            else if("FAILED".equals(info.status())&&request.state()!=State.FAILED
                    && !(id.kind()==TrainingId.Kind.RAG&&rag.findById(id.sourceId()).orElseThrow().getTrainingStatus()==null))ignored=true;
            else {
                if(request.state()==State.COMPLETED&&id.kind()!=TrainingId.Kind.RAG&&!"COMPLETED".equals(info.status()))throw QualitySupport.conflict();
                switch(id.kind()){
                    case VOICE -> {
                        var job=voices.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
                        if(request.state()==State.STARTED)job.markProcessing();
                        if(request.state()==State.FAILED)job.trainingFailure(request.failureCode().name(),request.retryAllowed());
                    }
                    case FACE -> {
                        var job=faces.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
                        if(request.state()==State.STARTED)job.markProcessing();
                        if(request.state()==State.FAILED)job.trainingFailure(request.failureCode().name(),request.retryAllowed());
                    }
                    case PROFILE -> {
                        if(request.state()==State.FAILED)
                            profiles.findByIdForUpdate(id.sourceId()).orElseThrow(QualitySupport::missing)
                                    .trainingFailure(request.failureCode().name(),request.retryAllowed());
                    }
                    case RAG -> {
                        var job=rag.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
                        if(request.state()==State.STARTED||request.state()==State.PROGRESS&&job.getTrainingStatus()==null)
                            job.trainingStarted(at);
                        if(request.state()==State.FAILED)job.trainingFailed(request.failureCode().name(),request.retryAllowed(),at);
                        if(request.state()==State.COMPLETED){
                            // A status report cannot activate a personality result that has not passed the versioned result callback.
                            if(!clone.isPersonalityTrainingCompleted()||clone.getSimilarityProfileRevision()==null
                                    ||clone.getSimilarityProfileRevision()<id.revision())throw QualitySupport.conflict();
                            job.trainingCompleted(clone.getCloneVersion(),at);
                        }
                    }
                }
            }
        }
        events.saveAndFlush(new TrainingLifecycleEvent(eventKey,key,fingerprint,request,!ignored));
        return new Received(key,false,ignored);
    }

    private void validateState(Lifecycle request) {
        if(request.state()==State.COMPLETED&&request.stage()!=Stage.COMPLETED
                ||request.state()!=State.COMPLETED&&request.stage()==Stage.COMPLETED
                ||request.state()==State.CREATED&&request.stage()!=Stage.QUEUED
                ||(request.state()==State.STARTED||request.state()==State.PROGRESS)&&request.stage()==Stage.QUEUED
                ||request.state()==State.FAILED&&(request.failureCode()==null||request.retryAllowed()==null)
                ||request.state()!=State.FAILED&&(request.failureCode()!=null||request.retryAllowed()!=null))throw QualitySupport.invalid();
    }
    private String fingerprint(Lifecycle request) {
        return QualitySupport.hash(request.sequence()+":"+request.state()+":"+request.stage()+":"
                +request.occurredAt().toInstant()+":"+request.cloneId()+":"+request.cloneVersion()+":"+request.failureCode()+":"+request.retryAllowed());
    }
    private void assertSequence(String key,long sequence,String fingerprint) {
        events.findFirstByTrainingJobIdAndSequenceOrderByIdAsc(key,sequence).ifPresent(previous->{
            if(!previous.getRequestHash().equals(fingerprint))throw QualitySupport.conflict();
        });
    }
    private Received supersededRagEvent(String key,TrainingId id,Lifecycle request) {
        var clone=clones.findLockedById(id.sourceId()).orElseThrow(QualitySupport::missing);
        if(!Objects.equals(request.cloneId(),clone.getId())||request.cloneVersion()>clone.getCloneVersion()
                ||!QualitySupport.correlation(key).equals(request.correlationId()))throw QualitySupport.conflict();
        validateState(request);
        if(QualitySupport.local(request.occurredAt()).isAfter(LocalDateTime.now().plusSeconds(5)))throw QualitySupport.invalid();
        String eventKey=QualitySupport.hash(key+":"+request.eventId()),hash=fingerprint(request);
        var previous=events.findByEventKey(eventKey);
        if(previous.isPresent()){
            if(!previous.get().getRequestHash().equals(hash))throw QualitySupport.conflict();
            return new Received(key,true,true);
        }
        assertSequence(key,request.sequence(),hash);
        events.saveAndFlush(new TrainingLifecycleEvent(eventKey,key,hash,request,false));
        return new Received(key,false,true);
    }
}
