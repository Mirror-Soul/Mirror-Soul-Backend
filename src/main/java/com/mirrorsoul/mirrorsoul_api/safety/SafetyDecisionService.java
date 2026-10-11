package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor
public class SafetyDecisionService {
    private final SafetyReportRepository reports;
    private final SafetyDecisionRepository decisions;
    private final SafetySanctionRepository sanctions;
    private final SafetyAppealRepository appeals;
    private final SafetyNoticeRepository notices;
    private final UserRepository users;
    private final SafetyPolicy policy;

    @Transactional
    public DecisionView decide(Long actorId,Long reportId,Decide request) {
        User actor=users.findById(actorId).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        SafetySupport.operator(actor);
        Long targetId=reports.findTargetId(reportId).orElseThrow(() -> new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND));
        if(Objects.equals(actorId,targetId)) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        // Every sanction update takes the target user lock before the report lock.
        User target=users.findByIdForUpdate(targetId).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        SafetyReport report=reports.findForUpdate(reportId).orElseThrow();
        if(Objects.equals(actorId,report.getReporterId()) || (target.getRole()!=UserRole.USER && actor.getRole()!=UserRole.SUPER_ADMIN))
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        String hash=SafetySupport.hash(request.action()+"|"+request.reasonCode()+"|"+request.expectedVersion()+"|"
            +request.expectedMemberVersion()+"|"+request.restrictionDays()+"|"+request.rationale().strip());
        String key=SafetySupport.key(request.idempotencyKey());
        var prior=decisions.findByReportIdAndRequestKey(reportId,key);
        if(prior.isPresent()) {
            if(!Objects.equals(prior.get().getActorId(),actorId) || !prior.get().getRequestHash().equals(hash))
                throw new GeneralException(GeneralErrorCode.REPORT_IDEMPOTENCY_CONFLICT);
            return DecisionView.from(prior.get());
        }
        if(!Objects.equals(report.getAssigneeId(),actorId) && actor.getRole()!=UserRole.SUPER_ADMIN)
            throw new GeneralException(GeneralErrorCode.FORBIDDEN,"The assigned administrator must decide.");
        SafetySupport.version(report.getVersion(),request.expectedVersion());
        validateReason(request);
        LocalDateTime now=SafetySupport.now();
        boolean initial=Set.of(Action.REJECT,Action.WARN,Action.RESTRICT).contains(request.action());
        boolean appealAction=request.action()==Action.APPROVE_APPEAL || request.action()==Action.REJECT_APPEAL;
        if(initial && report.getState()!=ReportState.RECEIVED && report.getState()!=ReportState.REVIEWING)
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT);
        if(appealAction && report.getState()!=ReportState.APPEALED) throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT);
        if(request.action()==Action.RELEASE && report.getState()!=ReportState.DECIDED) throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT);
        if((request.action()==Action.WARN || request.action()==Action.RESTRICT)
            && (report.isEvidencePurged() || !now.isBefore(report.getEvidenceExpiresAt())))
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT,"Evidence retention period expired.");
        SafetyAppeal appeal=null;
        if(appealAction) {
            appeal=appeals.findByReportIdAndState(reportId,AppealState.PENDING).orElseThrow(() -> new GeneralException(GeneralErrorCode.REPORT_CONFLICT));
            if(Objects.equals(decisions.findById(appeal.getDecisionId()).orElseThrow().getActorId(),actorId))
                throw new GeneralException(GeneralErrorCode.FORBIDDEN,"Appeals must be reviewed by another administrator.");
        }
        SafetySanction sanction=sanctions.findByReportId(reportId).orElse(null);
        boolean changesRestriction=request.action()==Action.RESTRICT || request.action()==Action.RELEASE
            || (request.action()==Action.APPROVE_APPEAL && sanction!=null && sanction.active(now));
        if(changesRestriction) SafetySupport.version(target.getVersion(),request.expectedMemberVersion());
        if(request.action()==Action.RESTRICT && (!target.isAccessAllowed() || sanction!=null))
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT,"Account cannot receive a new restriction.");
        if(request.action()==Action.RELEASE && (sanction==null || !sanction.active(now)))
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT,"No active restriction for this report.");
        SafetyDecision decision=decisions.saveAndFlush(new SafetyDecision(reportId,actorId,request.action(),request.reasonCode(),
            request.rationale().strip(),key,hash,now));
        if(request.action()==Action.RESTRICT) {
            int days=request.restrictionDays()==null ? policy.defaultRestrictionDays() : request.restrictionDays();
            sanction=sanctions.saveAndFlush(new SafetySanction(reportId,decision.getId(),targetId,now,days));
        } else if(request.action()==Action.RELEASE || (request.action()==Action.APPROVE_APPEAL && sanction!=null && sanction.active(now))) {
            sanction.release(actorId,now);sanctions.flush();
        }
        if(changesRestriction) {
            LocalDateTime until=sanctions.findByUserIdAndReleasedAtIsNullAndExpiresAtAfter(targetId,now).stream()
                .map(SafetySanction::getExpiresAt).max(LocalDateTime::compareTo).orElse(null);
            target.updateSafetyRestriction(until);
        }
        if(appeal!=null) appeal.resolve(request.action()==Action.APPROVE_APPEAL ? AppealState.ACCEPTED : AppealState.REJECTED,actorId,now);
        report.decide(decision.getId(),now);report.retainThrough(now.plusDays(policy.retentionDays()));
        reports.flush();users.flush();
        SafetyNotice reporterNotice=notices.saveAndFlush(new SafetyNotice(report.getReporterId(),reportId,decision.getId(),request.action(),
            request.reasonCode(),"신고 검토 결과가 등록되었습니다. 결과: "+request.action().name(),now));
        SafetyNotice targetNotice=null;
        if(request.action()!=Action.REJECT) targetNotice=notices.saveAndFlush(new SafetyNotice(targetId,reportId,decision.getId(),request.action(),
            request.reasonCode(),request.rationale().strip(),now));
        decision.result(report.getVersion(),target.getVersion(),sanction==null ? null : sanction.getId(),reporterNotice.getId(),
            targetNotice==null ? null : targetNotice.getId());decisions.flush();
        log.info("Safety decision committed reportId={} decisionId={} actorId={} action={}",reportId,decision.getId(),actorId,request.action());
        return DecisionView.from(decision);
    }
    private void validateReason(Decide request) {
        boolean valid=switch(request.action()) {
            case WARN, RESTRICT -> request.reasonCode()==Reason.VIOLATION_CONFIRMED;
            case REJECT -> request.reasonCode()==Reason.INSUFFICIENT_EVIDENCE || request.reasonCode()==Reason.NO_VIOLATION;
            case RELEASE -> request.reasonCode()==Reason.RESTRICTION_COMPLETED;
            case APPROVE_APPEAL -> request.reasonCode()==Reason.APPEAL_ACCEPTED;
            case REJECT_APPEAL -> request.reasonCode()==Reason.APPEAL_REJECTED;
        };
        if(!valid || (request.action()!=Action.RESTRICT && request.restrictionDays()!=null)
            || (request.restrictionDays()!=null && (request.restrictionDays()<1 || request.restrictionDays()>30)))
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,"Invalid decision reason or duration.");
    }
}
