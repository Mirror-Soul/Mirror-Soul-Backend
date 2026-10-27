package com.mirrorsoul.mirrorsoul_api.support;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.cloneprofile.CloneProfileGenerationJob;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyCallDiagnosticRepository;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import java.util.*;
@Component @RequiredArgsConstructor
public class SupportDiagnostics {
 private final EntityManager em;
 private final SafetyCallDiagnosticRepository diagnostics;
 public void validate(Long owner,Create c) {
  if(c.transactionId()!=null) {TalkTimeTransaction t=em.find(TalkTimeTransaction.class,c.transactionId());if(t==null||!owner.equals(t.getUser().getId())) SupportAccess.forbidden();}
  if(c.callId()!=null) {VideoCall t=em.find(VideoCall.class,c.callId());if(t==null||(!owner.equals(t.getUser().getId())&&!owner.equals(t.getClone().getUser().getId()))) SupportAccess.forbidden();}
  if((c.jobId()==null)!=(c.jobType()==null)) throw new com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException(com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode.INVALID_PARAMETER);
  if(c.jobId()!=null) {
   Long member=switch(c.jobType()) {
    case VOICE -> {var j=em.find(VoiceTrainingJob.class,c.jobId());yield j==null?null:j.getUser().getId();}
    case FACE -> {var j=em.find(FaceTrainingJob.class,c.jobId());yield j==null?null:j.getUser().getId();}
    case VALUE_BALANCE -> {var j=em.find(ValueBalanceAnalysisJob.class,c.jobId());yield j==null?null:j.getUser().getId();}
    case CLONE_PROFILE -> {var j=em.find(CloneProfileGenerationJob.class,c.jobId());yield j==null?null:j.getClone().getUser().getId();}
   };
   if(!owner.equals(member)) SupportAccess.forbidden();
  }
 }
 public Diagnostic view(SupportTicket t) {
  List<String> codes=t.getCallId()==null?List.of():diagnostics.findByCallIdOrderByIdDesc(t.getCallId(),org.springframework.data.domain.PageRequest.of(0,100)).stream().map(d->d.getCode().name()).toList();
  String status=null;
  if(t.getJobId()!=null) status=switch(t.getJobType()) {
   case VOICE -> {var j=em.find(VoiceTrainingJob.class,t.getJobId());yield j==null?"UNAVAILABLE":j.getStatus().name();}
   case FACE -> {var j=em.find(FaceTrainingJob.class,t.getJobId());yield j==null?"UNAVAILABLE":j.getStatus().name();}
   case VALUE_BALANCE -> {var j=em.find(ValueBalanceAnalysisJob.class,t.getJobId());yield j==null?"UNAVAILABLE":j.getStatus().name();}
   case CLONE_PROFILE -> {var j=em.find(CloneProfileGenerationJob.class,t.getJobId());yield j==null?"UNAVAILABLE":j.getStatus().name();}
  };
  // Never disclose worker exception messages, provider payloads, or arbitrary error codes.
  String error="FAILED".equals(status)?"AI_JOB_FAILED":"UNAVAILABLE".equals(status)?"JOB_UNAVAILABLE":null;
  return new Diagnostic(t.getCallId(),codes,t.getJobType(),t.getJobId(),status,error);
 }
}
