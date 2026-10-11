package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.*;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.service.AdminMemberService;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class SafetyReportService {
    private final SafetyReportRepository reports;
    private final SafetyDecisionRepository decisions;
    private final SafetyAppealRepository appeals;
    private final UserRepository users;
    private final VideoCallRepository calls;
    private final ChatMessageRepository messages;
    private final ChatRoomMemberRepository memberships;
    private final SafetyPolicy policy;

    @Transactional
    public ReportView submit(Long reporterId,Submit request) {
        User reporter=users.findByIdForUpdate(reporterId).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if(!reporter.isAccessAllowed()) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        User target=users.findByUuid(request.targetUserUuid()).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if(Objects.equals(reporterId,target.getId())) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER,"Self report is not allowed.");
        String context=SafetySupport.hash(target.getId()+"|"+request.type()+"|"+request.callId()+"|"+request.messageId());
        String hash=SafetySupport.hash(context+"|"+request.description().strip());
        String key=SafetySupport.key(request.idempotencyKey());
        var prior=reports.findByReporterIdAndRequestKey(reporterId,key);
        if(prior.isPresent()) {
            if(!prior.get().getRequestHash().equals(hash)) throw new GeneralException(GeneralErrorCode.REPORT_IDEMPOTENCY_CONFLICT);
            return ReportView.from(prior.get());
        }
        String snapshot=validateContext(reporter,target,request);
        LocalDateTime now=SafetySupport.now();
        var duplicate=reports.findFirstByReporterIdAndContextHashAndCreatedAtAfterOrderByIdDesc(reporterId,context,now.minusHours(24));
        if(duplicate.isPresent()) {
            log.warn("Duplicate safety report reporterId={} reportId={}",reporterId,duplicate.get().getId());
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT,"A report for this context was already submitted in the past 24 hours.");
        }
        if(reports.countByReporterIdAndCreatedAtAfter(reporterId,now.minusHours(1))>=policy.hourlyLimit()
                || reports.countByReporterIdAndCreatedAtAfter(reporterId,now.minusDays(1))>=policy.dailyLimit()) {
            log.warn("Safety report rate limit exceeded reporterId={}",reporterId);
            throw new GeneralException(GeneralErrorCode.REPORT_RATE_LIMITED);
        }
        Priority priority=switch(request.type()) {
            case THREATS -> Priority.URGENT;
            case SEXUAL_CONTENT, PRIVACY -> Priority.HIGH;
            default -> Priority.NORMAL;
        };
        SafetyReport report=reports.saveAndFlush(new SafetyReport(reporterId,target.getId(),request.type(),priority,
            request.callId(),request.messageId(),request.description().strip(),snapshot,key,hash,context,now,policy.retentionDays()));
        log.info("Safety report received reportId={} priority={} callId={}",report.getId(),priority,request.callId());
        if(priority==Priority.URGENT) log.error("Urgent safety report received reportId={} callId={}",report.getId(),request.callId());
        return ReportView.from(report);
    }

    private String validateContext(User reporter,User target,Submit request) {
        if(request.callId()!=null) {
            VideoCall call=calls.findByIdWithParticipants(request.callId()).orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_PARAMETER,"Invalid report call."));
            Set<Long> participants=new HashSet<>(List.of(call.getUser().getId(),call.getClone().getUser().getId()));
            if(!participants.contains(reporter.getId()) || !participants.contains(target.getId())) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        if(request.messageId()!=null) {
            ChatMessage message=messages.findById(request.messageId()).orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_PARAMETER,"Invalid report message."));
            if(!Objects.equals(message.getSender().getId(),target.getId())
                || memberships.countMembershipAt(message.getChatRoom().getId(),reporter.getId(),message.getCreatedAt())==0)
                throw new GeneralException(GeneralErrorCode.FORBIDDEN);
            return message.getContent();
        }
        return null;
    }

    public Page<ReportView> mine(Long reporterId,int page,int size) {
        return reports.findByReporterId(reporterId,AdminMemberService.pageable(page,size)).map(ReportView::from);
    }
    public ReportView mine(Long reporterId,Long id) {
        SafetyReport r=report(id);
        if(!Objects.equals(r.getReporterId(),reporterId)) throw new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND);
        return ReportView.from(r);
    }
    public Page<ReportView> list(Long actorId,Priority priority,ReportState state,Long assigneeId,int page,int size) {
        SafetySupport.reader(user(actorId));
        var result=reports.findAll((root,query,cb) -> {
            List<Predicate> predicates=new ArrayList<>();
            if(priority!=null) predicates.add(cb.equal(root.get("priority"),priority));
            if(state!=null) predicates.add(cb.equal(root.get("state"),state));
            if(assigneeId!=null) predicates.add(cb.equal(root.get("assigneeId"),assigneeId));
            return cb.and(predicates.toArray(Predicate[]::new));
        },AdminMemberService.pageable(page,size)).map(ReportView::from);
        log.info("Admin reports viewed actorId={}",actorId);
        return result;
    }
    public Detail detail(Long actorId,Long id) {
        SafetySupport.reader(user(actorId));SafetyReport r=report(id);
        log.info("Admin report viewed actorId={} reportId={}",actorId,id);
        return new Detail(ReportView.from(r),user(r.getTargetId()).getVersion(),
            r.getLatestDecisionId()==null ? null : decisions.findById(r.getLatestDecisionId()).map(DecisionView::from).orElse(null),
            appeals.findByReportIdAndState(id,AppealState.PENDING).map(AppealView::from).orElse(null));
    }
    public Page<DecisionView> history(Long actorId,Long id,int page,int size) {
        SafetySupport.reader(user(actorId));report(id);
        return decisions.findByReportId(id,AdminMemberService.pageable(page,size)).map(DecisionView::from);
    }
    @Transactional
    public ReportView assign(Long actorId,Long id,Assign request) {
        User actor=user(actorId);SafetySupport.operator(actor);
        if(!Objects.equals(actorId,request.assigneeId()) && actor.getRole()!=UserRole.SUPER_ADMIN) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        SafetySupport.operator(user(request.assigneeId()));
        SafetyReport report=reports.findForUpdate(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND));
        SafetySupport.version(report.getVersion(),request.expectedVersion());
        if(report.getState()==ReportState.DECIDED) throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT);
        if(Objects.equals(report.getTargetId(),request.assigneeId()) || Objects.equals(report.getReporterId(),request.assigneeId())) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        var appeal=appeals.findByReportIdAndState(id,AppealState.PENDING);
        if(appeal.isPresent() && Objects.equals(decisions.findById(appeal.get().getDecisionId()).orElseThrow().getActorId(),request.assigneeId()))
            throw new GeneralException(GeneralErrorCode.FORBIDDEN,"Appeals must be reviewed by another administrator.");
        report.assign(request.assigneeId(),SafetySupport.now());reports.flush();
        return ReportView.from(report);
    }
    private SafetyReport report(Long id) { return reports.findById(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND)); }
    private User user(Long id) { return users.findById(id).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND)); }
}
