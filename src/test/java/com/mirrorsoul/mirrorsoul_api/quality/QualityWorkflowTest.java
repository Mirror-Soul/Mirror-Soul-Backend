package com.mirrorsoul.mirrorsoul_api.quality;

import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.access.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.common.jpaAuditing.JpaAuditingConfig;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.voice.VoiceTrainingResultDTO;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.service.*;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.mirrorsoul.mirrorsoul_api.quality.CallQualityDtos.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({QualityWorkflowTest.Config.class,JpaAuditingConfig.class,TrainingReadService.class,TrainingControlService.class,
        CallQualityService.class,CallDiagnosticsReadService.class,AdminPermissionService.class,AdminAuditWriter.class,
        VoiceTrainingResultService.class,CloneSimilarityService.class,CloneReadinessService.class,AwsS3Properties.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class QualityWorkflowTest {
    @TestConfiguration static class Config {
        @Bean JdbcTemplate qualityJdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean NamedParameterJdbcTemplate qualityNamed(JdbcTemplate jdbc){return new NamedParameterJdbcTemplate(jdbc);}
    }
    @Autowired TrainingReadService reads;
    @Autowired TrainingControlService training;
    @Autowired CallQualityService quality;
    @Autowired CallDiagnosticsReadService diagnostics;
    @Autowired UserRepository users;
    @Autowired CloneRepository clones;
    @Autowired VoiceTrainingJobRepository voices;
    @Autowired FaceTrainingJobRepository faces;
    @Autowired CloneProfileGenerationJobRepository profiles;
    @Autowired RagProfileJobRepository rag;
    @Autowired VideoCallRepository calls;
    @Autowired CallStageMeasurementRepository samples;
    @Autowired CallQualityEventRepository events;
    @Autowired TrainingRetryRepository retries;
    @Autowired AdminAuditEventRepository audits;
    @Autowired AiVoiceProfileRepository voiceProfiles;
    @Autowired AiFaceProfileRepository faceProfiles;
    @Autowired VoiceTrainingResultService voiceResults;
    @Autowired CloneReadinessService readiness;
    @Autowired CloneSimilarityService similarity;
    @Autowired PlatformTransactionManager transactions;

    User user(UserRole role){
        return users.saveAndFlush(User.builder().uuid(UUID.randomUUID()).email(UUID.randomUUID()+"@example.com")
                .passwordHash("hash").role(role).status(UserStatus.ACTIVE).createdAt(LocalDateTime.now()).build());
    }
    Clone clone(User user){return clones.saveAndFlush(Clone.builder().user(user).createdAt(LocalDateTime.now()).build());}
    void tx(Runnable work){new TransactionTemplate(transactions).executeWithoutResult(s->work.run());}
    VoiceTrainingJob voice(User user,boolean failed){
        var job=VoiceTrainingJob.create(user);job.addFile(null,"test-bucket","private-audio");
        if(failed)job.markDispatchFailed("raw name secret");
        return voices.saveAndFlush(job);
    }
    VideoCall call(Clone clone,CallMediaType type){
        var call=VideoCall.builder().user(clone.getUser()).clone(clone).roomId(UUID.randomUUID().toString()).mediaType(type).build();
        ReflectionTestUtils.setField(call,"startedAt",LocalDateTime.now().minusMinutes(10));
        call.start(LocalDateTime.now().minusMinutes(5),1800);
        return calls.saveAndFlush(call);
    }
    Event event(VideoCall call,String turn,Step step,OffsetDateTime at){
        return new Event(turn+"-"+step,turn,step,at,UUID.fromString(call.diagnosticCorrelationId()),
                call.getClone().getId(),call.getCloneVersion(),null,null);
    }
    TrainingDtos.Lifecycle lifecycle(Clone clone,String key,long seq,TrainingDtos.State state,TrainingDtos.Stage stage){
        return new TrainingDtos.Lifecycle("event-"+seq,seq,state,stage,OffsetDateTime.now(),clone.getId(),clone.getCloneVersion(),
                QualitySupport.correlation(key),state==TrainingDtos.State.FAILED?QualitySupport.Failure.PROVIDER_UNAVAILABLE:null,
                state==TrainingDtos.State.FAILED?true:null);
    }
    @Test void unifiedJobsExposeSafeFailureClassificationAndStableIdentifiers(){
        var owner=user(UserRole.USER);var clone=clone(owner);var voice=voice(owner,true);
        var face=faces.saveAndFlush(FaceTrainingJob.create(owner,FaceTrainingJobSource.ONBOARDING_FACE));
        var profile=profiles.saveAndFlush(CloneProfileGenerationJob.create(clone,CloneProfileTrigger.PERSONALITY_UPDATED,"a".repeat(64),"prompt"));
        var delivery=RagProfileJob.create(clone.getId(),LocalDateTime.now());delivery.request(LocalDateTime.now());rag.saveAndFlush(delivery);
        var list=reads.list(null,owner.getId(),null,0,20);
        assertThat(list.getTotalElements()).isEqualTo(4);
        assertThat(list.getContent()).extracting(TrainingDtos.Job::trainingJobId)
                .containsExactlyInAnyOrder("VOICE-"+voice.getId(),"FACE-"+face.getId(),"PROFILE-"+profile.getId(),"RAG-"+clone.getId()+"-1");
        var view=reads.get("VOICE-"+voice.getId());
        assertThat(view.failureCode()).isEqualTo(QualitySupport.Failure.DISPATCH_FAILED);
        assertThat(view.toString()).doesNotContain("private-audio","raw name secret");
        assertThat(view.retryAllowed()).isTrue();assertThat(view.retriedFromJobId()).isNull();
        assertThat(view.correlationId()).isEqualTo(QualitySupport.correlation(view.trainingJobId()));
    }
    @Test void failedOnlyRetryIsPermissionCheckedIdempotentAndAudited(){
        var owner=user(UserRole.USER);clone(owner);var job=voice(owner,true);
        var viewer=user(UserRole.ADMIN_VIEWER);var admin=user(UserRole.SUPER_ADMIN);
        String key="VOICE-"+job.getId();var request=new TrainingDtos.Retry("support-case");
        assertThatThrownBy(()->training.retry(viewer.getId(),key,request)).isInstanceOf(GeneralException.class);
        var result=training.retry(admin.getId(),key,request);
        assertThat(training.retry(admin.getId(),key,request)).isEqualTo(result);
        var replacement=reads.get(result.newTrainingJobId());
        assertThat(replacement.status()).isEqualTo("PENDING");assertThat(replacement.retryCount()).isEqualTo(1);
        assertThat(replacement.retriedFromJobId()).isEqualTo(key);
        assertThat(reads.get(key).retryAllowed()).isFalse();
        assertThat(voices.findById(replacement.sourceJobId()).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.PENDING);
        assertThatThrownBy(()->training.retry(admin.getId(),key,new TrainingDtos.Retry("different-key"))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(()->training.retry(admin.getId(),result.newTrainingJobId(),new TrainingDtos.Retry("pending-retry"))).isInstanceOf(GeneralException.class);
        assertThat(audits.findAll()).anyMatch(a->"CLONE_TRAINING_RETRY".equals(a.getAction())&&admin.getId().equals(a.getStaffId()));
    }
    @Test void callbacksHandleReplaysOlderSequencesAndFailedAttemptFencing(){
        var owner=user(UserRole.USER);var clone=clone(owner);var job=voice(owner,false);String key="VOICE-"+job.getId();
        var started=lifecycle(clone,key,2,TrainingDtos.State.STARTED,TrainingDtos.Stage.TRAINING);
        assertThat(training.event(key,started).ignored()).isFalse();
        assertThat(training.event(key,started).duplicate()).isTrue();
        assertThat(training.event(key,lifecycle(clone,key,1,TrainingDtos.State.CREATED,TrainingDtos.Stage.QUEUED)).ignored()).isTrue();
        var oldSequence=new TrainingDtos.Lifecycle("different-old-event",1L,TrainingDtos.State.PROGRESS,TrainingDtos.Stage.TRAINING,
                started.occurredAt(),clone.getId(),clone.getCloneVersion(),started.correlationId(),null,null);
        assertThatThrownBy(()->training.event(key,oldSequence)).isInstanceOf(GeneralException.class);
        var conflicting=new TrainingDtos.Lifecycle(started.eventId(),2L,TrainingDtos.State.PROGRESS,TrainingDtos.Stage.TRAINING,
                started.occurredAt(),clone.getId(),clone.getCloneVersion(),started.correlationId(),null,null);
        assertThatThrownBy(()->training.event(key,conflicting)).isInstanceOf(GeneralException.class);
        training.event(key,lifecycle(clone,key,3,TrainingDtos.State.FAILED,TrainingDtos.Stage.TRAINING));
        assertThat(reads.get(key).status()).isEqualTo("FAILED");assertThat(reads.get(key).retryAllowed()).isTrue();
        assertThat(training.event(key,lifecycle(clone,key,4,TrainingDtos.State.COMPLETED,TrainingDtos.Stage.COMPLETED)).ignored()).isTrue();
        assertThat(reads.get(key).status()).isEqualTo("FAILED");
    }
    @Test void completionCannotActivateUnvalidatedAssets(){
        var owner=user(UserRole.USER);var clone=clone(owner);var job=voice(owner,false);String key="VOICE-"+job.getId();
        assertThatThrownBy(()->training.event(key,lifecycle(clone,key,1,TrainingDtos.State.COMPLETED,TrainingDtos.Stage.COMPLETED)))
                .isInstanceOf(GeneralException.class);
        assertThat(voices.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(VoiceTrainingJobStatus.PENDING);
    }
    @Test void simultaneousRetryCommandsCreateOneReplacement() throws Exception {
        var owner=user(UserRole.USER);clone(owner);var job=voice(owner,true);var actor=user(UserRole.SUPER_ADMIN);
        String key="VOICE-"+job.getId();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try{
            Callable<TrainingDtos.RetryResult> work=()->{gate.await();return training.retry(actor.getId(),key,new TrainingDtos.Retry("same-key"));};
            var a=pool.submit(work);var b=pool.submit(work);gate.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
    }
    @Test void ragManualRetryIsIndependentOfAutomaticAttemptsAndReplaysOldRevision() {
        var owner=user(UserRole.USER);var clone=clone(owner);var actor=user(UserRole.SUPER_ADMIN);
        var job=RagProfileJob.create(clone.getId(),LocalDateTime.now());job.request(LocalDateTime.now());
        for(int i=0;i<5;i++){
            String token=job.claim(LocalDateTime.now());job.finish(token,1,"HTTP_503",LocalDateTime.now());
        }
        rag.saveAndFlush(job);String old="RAG-"+clone.getId()+"-1";
        assertThat(reads.get(old).retryCount()).isZero();assertThat(reads.get(old).retryAllowed()).isTrue();
        var request=new TrainingDtos.Retry("rag-support-retry");
        var result=training.retry(actor.getId(),old,request);
        assertThat(result.newTrainingJobId()).isEqualTo("RAG-"+clone.getId()+"-2");
        assertThat(training.retry(actor.getId(),old,request)).isEqualTo(result);
        assertThat(reads.get(result.newTrainingJobId()).retryCount()).isEqualTo(1);
        var stale=lifecycle(clone,old,7,TrainingDtos.State.COMPLETED,TrainingDtos.Stage.COMPLETED);
        assertThat(training.event(old,stale).ignored()).isTrue();assertThat(training.event(old,stale).duplicate()).isTrue();
        assertThat(reads.get(result.newTrainingJobId()).status()).isEqualTo("PENDING");
    }
    @Test void stalledJobsUseAcceptedActivityAndTerminalJobsAreExcluded() {
        var owner=user(UserRole.USER);var clone=clone(owner);var active=voice(owner,false);voice(owner,true);
        var cutoff=LocalDateTime.now().plusMinutes(1);
        long before=reads.countStalled(cutoff);
        assertThat(before).isGreaterThanOrEqualTo(1);
        String key="VOICE-"+active.getId();
        training.event(key,lifecycle(clone,key,1,TrainingDtos.State.FAILED,TrainingDtos.Stage.TRAINING));
        assertThat(reads.countStalled(cutoff)).isEqualTo(before-1);
    }
    @Test void ragDeliveryWaitsForValidatedTrainingResultAndRecordsRetryableFailure() {
        var owner=user(UserRole.USER);var clone=clone(owner);
        var job=RagProfileJob.create(clone.getId(),LocalDateTime.now());job.request(LocalDateTime.now());
        String token=job.claim(LocalDateTime.now());job.finish(token,1,null,LocalDateTime.now());rag.saveAndFlush(job);
        String key="RAG-"+clone.getId()+"-1";
        assertThat(reads.get(key).status()).isEqualTo("PROCESSING");assertThat(reads.get(key).completedAt()).isNull();
        training.event(key,lifecycle(clone,key,1,TrainingDtos.State.STARTED,TrainingDtos.Stage.TRAINING));
        assertThatThrownBy(()->training.event(key,lifecycle(clone,key,2,TrainingDtos.State.COMPLETED,TrainingDtos.Stage.COMPLETED)))
                .isInstanceOf(GeneralException.class);
        training.event(key,lifecycle(clone,key,2,TrainingDtos.State.FAILED,TrainingDtos.Stage.TRAINING));
        assertThat(reads.get(key).status()).isEqualTo("FAILED");assertThat(reads.get(key).retryAllowed()).isTrue();
        similarity.completePersonality(clone.getId(),new com.mirrorsoul.mirrorsoul_api.dto.ClonePersonalityCompleteRequest(
                CloneSimilarityCalculator.VERSION,java.math.BigDecimal.valueOf(80),java.math.BigDecimal.valueOf(70),java.math.BigDecimal.ZERO,1L));
        assertThat(clones.findById(clone.getId()).orElseThrow().getCloneVersion()).isZero();
    }
    @Test void ragCompletionRecordsTheValidatedResultVersion() {
        var owner=user(UserRole.USER);var clone=clone(owner);
        var job=RagProfileJob.create(clone.getId(),LocalDateTime.now());job.request(LocalDateTime.now());rag.saveAndFlush(job);
        similarity.completePersonality(clone.getId(),new com.mirrorsoul.mirrorsoul_api.dto.ClonePersonalityCompleteRequest(
                CloneSimilarityCalculator.VERSION,java.math.BigDecimal.valueOf(80),java.math.BigDecimal.valueOf(70),java.math.BigDecimal.ZERO,1L));
        String key="RAG-"+clone.getId()+"-1";
        training.event(key,lifecycle(clone,key,1,TrainingDtos.State.COMPLETED,TrainingDtos.Stage.COMPLETED));
        assertThat(reads.get(key).status()).isEqualTo("COMPLETED");
        assertThat(reads.get(key).cloneVersion()).isEqualTo(clones.findById(clone.getId()).orElseThrow().getCloneVersion());
    }
    @Test void retryAndLateOldResultPreserveWorkingTwinUntilSuccessfulReplacement(){
        var owner=user(UserRole.USER);var clone=clone(owner);var oldVoice=voice(owner,false);
        tx(()->{
            var c=clones.findLockedById(clone.getId()).orElseThrow();var v=voices.findLockedById(oldVoice.getId()).orElseThrow();v.complete();
            voiceProfiles.save(AiVoiceProfile.create(c,v,"working-voice","ACTIVE",true));
            var f=faces.saveAndFlush(FaceTrainingJob.create(owner,FaceTrainingJobSource.ONBOARDING_FACE));f.complete();
            faceProfiles.save(AiFaceProfile.ready(c,f,"test-bucket","profile","portrait","manifest",null));
            c.updateVoiceSimilarity(v.getId(),java.math.BigDecimal.valueOf(70));c.updatePersonalityTrainingCompleted(true);
        });
        readiness.refresh(clone.getId());
        var before=clones.findById(clone.getId()).orElseThrow();assertThat(before.getStatus()).isEqualTo("READY");
        long version=before.getCloneVersion();
        var failed=voice(owner,true);var actor=user(UserRole.SUPER_ADMIN);
        var retried=training.retry(actor.getId(),"VOICE-"+failed.getId(),new TrainingDtos.Retry("new-voice"));
        voiceResults.handle(new VoiceTrainingResultDTO("VOICE_TRAINING_STATUS",failed.getId(),owner.getUuid(),clone.getId(),"COMPLETED",
                new VoiceTrainingResultDTO.Result("late-old",java.math.BigDecimal.valueOf(90),null),null));
        assertThat(clones.findById(clone.getId()).orElseThrow().getCloneVersion()).isEqualTo(version);
        assertThat(voiceProfiles.findFirstByCloneIdAndActiveTrueOrderByCreatedAtDescIdDesc(clone.getId()).orElseThrow().getElevenlabsVoiceId()).isEqualTo("working-voice");
        var replacement=reads.get(retried.newTrainingJobId());
        voiceResults.handle(new VoiceTrainingResultDTO("VOICE_TRAINING_STATUS",replacement.sourceJobId(),owner.getUuid(),clone.getId(),"COMPLETED",
                new VoiceTrainingResultDTO.Result("new-voice",java.math.BigDecimal.valueOf(90),null),null));
        assertThat(clones.findById(clone.getId()).orElseThrow().getStatus()).isEqualTo("READY");
        assertThat(reads.get(retried.newTrainingJobId()).cloneVersion()).isGreaterThan(version);
    }
    @Test void reorderedCallStagesMaterializeOnceAndProvideRealPercentiles(){
        var call=call(clone(user(UserRole.USER)),CallMediaType.VOICE);
        var start=OffsetDateTime.now().minusSeconds(10);
        for(int i=1;i<=20;i++){
            String turn="turn"+i;var stt=start.plusSeconds(i);
            // Keep every point in the past and deliberately deliver the final anchor first.
            stt=stt.minusSeconds(30);
            var client=event(call,turn,Step.CLIENT_DELIVERED,stt.plusNanos((i*10L+40)*1_000_000));
            quality.receive(call.getId(),client);
            quality.receive(call.getId(),event(call,turn,Step.TTS_FIRST_AUDIO,stt.plusNanos((i*10L+20)*1_000_000)));
            quality.receive(call.getId(),event(call,turn,Step.LLM_FIRST_TOKEN,stt.plusNanos(i*10L*1_000_000)));
            quality.receive(call.getId(),event(call,turn,Step.STT_ENDED,stt));
            assertThat(quality.receive(call.getId(),client).duplicate()).isTrue();
        }
        var result=diagnostics.get(call.getId());
        assertThat(result.eventCount()).isEqualTo(80);assertThat(result.incompleteTurns()).isZero();
        assertThat(result.measurements()).hasSize(80);
        var latency=result.distribution().stages().stream().filter(p->p.stage()==Stage.STT_TO_LLM).findFirst().orElseThrow();
        assertThat(latency.sampleCount()).isEqualTo(20);assertThat(latency.p50Ms()).isEqualTo(100);assertThat(latency.p95Ms()).isEqualTo(190);
        assertThat(result.distribution().stages().stream().filter(p->p.stage()==Stage.TTS_TO_VIDEO).findFirst().orElseThrow().p95Ms()).isNull();
        assertThat(result.toString()).doesNotContain("private-audio","working-voice","passwordHash");
    }
    @Test void missingAndInvalidAnchorsDoNotFabricateMeasurements(){
        var call=call(clone(user(UserRole.USER)),CallMediaType.VOICE);var at=OffsetDateTime.now().minusSeconds(10);
        quality.receive(call.getId(),event(call,"missing",Step.STT_ENDED,at));
        quality.receive(call.getId(),event(call,"invalid",Step.STT_ENDED,at));
        quality.receive(call.getId(),event(call,"invalid",Step.LLM_FIRST_TOKEN,at.minusSeconds(1)));
        var result=diagnostics.get(call.getId());
        assertThat(result.measurements()).isEmpty();assertThat(result.incompleteTurns()).isEqualTo(2);
        assertThat(result.invalidStagePairs()).isEqualTo(1);
        assertThat(result.distribution().stages()).allMatch(p->p.sampleCount()==0&&p.p50Ms()==null&&p.p95Ms()==null);
    }
    @Test void telemetryIsBoundToCallAndEventPayloadAndIncludesOnlySafeConnectionFields(){
        var call=call(clone(user(UserRole.USER)),CallMediaType.VIDEO);var at=OffsetDateTime.now().minusSeconds(10);
        var bad=new Event("1","t",Step.STT_ENDED,at,UUID.randomUUID(),call.getClone().getId(),call.getCloneVersion(),null,null);
        assertThatThrownBy(()->quality.receive(call.getId(),bad)).isInstanceOf(GeneralException.class);
        var first=event(call,"t",Step.STT_ENDED,at);quality.receive(call.getId(),first);
        var alias=new Event("another-id","t",Step.STT_ENDED,at,first.correlationId(),first.cloneId(),first.cloneVersion(),null,null);
        assertThat(quality.receive(call.getId(),alias).duplicate()).isTrue();
        var reusedAlias=new Event("another-id","another-turn",Step.STT_ENDED,at,first.correlationId(),first.cloneId(),first.cloneVersion(),null,null);
        assertThatThrownBy(()->quality.receive(call.getId(),reusedAlias)).isInstanceOf(GeneralException.class);
        assertThatThrownBy(()->quality.receive(call.getId(),event(call,"t",Step.STT_ENDED,at.plusSeconds(1)))).isInstanceOf(GeneralException.class);
        quality.receive(call.getId(),new Event("link","t",Step.CONNECTION,at,UUID.fromString(call.diagnosticCorrelationId()),
                call.getClone().getId(),call.getCloneVersion(),new Link(Connection.POOR,2.0,15.0,80.0,512.0),null));
        quality.receive(call.getId(),new Event("error","t",Step.ERROR,at,UUID.fromString(call.diagnosticCorrelationId()),
                call.getClone().getId(),call.getCloneVersion(),null,CallQualityDtos.Error.TTS_TIMEOUT));
        var result=diagnostics.get(call.getId());
        assertThat(result.connection().quality()).isEqualTo(Connection.POOR);
        assertThat(result.errors()).extracting(Signal::code).contains("TTS_TIMEOUT");
    }
    @Test void aggregateStatisticsRespectTimeMediaCloneAndVersionScope(){
        var clone=clone(user(UserRole.USER));var voice=call(clone,CallMediaType.VOICE);var video=call(clone,CallMediaType.VIDEO);
        var at=OffsetDateTime.now().minusSeconds(10);
        quality.receive(voice.getId(),event(voice,"t",Step.STT_ENDED,at));
        quality.receive(voice.getId(),event(voice,"t",Step.LLM_FIRST_TOKEN,at.plusNanos(30_000_000)));
        quality.receive(video.getId(),event(video,"t",Step.STT_ENDED,at));
        quality.receive(video.getId(),event(video,"t",Step.LLM_FIRST_TOKEN,at.plusNanos(90_000_000)));
        var window=diagnostics.distribution(at.minusSeconds(1),at.plusSeconds(1),null,clone.getId(),0L,CallMediaType.VOICE);
        var stage=window.stages().stream().filter(p->p.stage()==Stage.STT_TO_LLM).findFirst().orElseThrow();
        assertThat(stage.sampleCount()).isEqualTo(1);assertThat(stage.p95Ms()).isEqualTo(30);
        var empty=diagnostics.distribution(at.plusSeconds(1),at.plusSeconds(2),null,clone.getId(),0L,CallMediaType.VOICE);
        assertThat(empty.stages()).allMatch(p->p.sampleCount()==0);
        assertThatThrownBy(()->diagnostics.distribution(at,at.plusDays(32),null,null,null,null)).isInstanceOf(GeneralException.class);
    }
}
