package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.service.AdminMemberService;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class SafetyMemberService {
    private final UserRepository users;
    private final SafetyReportRepository reports;
    private final SafetyDecisionRepository decisions;
    private final SafetyAppealRepository appeals;
    private final SafetyNoticeRepository notices;
    private final SafetySanctionRepository sanctions;
    private final SafetyPolicy policy;

    @Transactional
    public AppealView appeal(Long userId,Long reportId,Appeal request) {
        User user=users.findByIdForUpdate(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if(!user.isAccessAllowed()) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        SafetyReport report=reports.findForUpdate(reportId).orElseThrow(() -> new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND));
        if(!Objects.equals(report.getTargetId(),userId)) throw new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND);
        var existing=appeals.findByDecisionId(request.decisionId());
        String key=SafetySupport.key(request.idempotencyKey());
        if(existing.isPresent()) {
            SafetyAppeal a=existing.get();
            if(!Objects.equals(a.getReportId(),reportId) || !Objects.equals(a.getUserId(),userId)
                || !a.getRequestKey().equals(key) || !a.getRequestHash().equals(SafetySupport.hash(request.explanation().strip())))
                throw new GeneralException(GeneralErrorCode.REPORT_IDEMPOTENCY_CONFLICT);
            return AppealView.from(a);
        }
        if(report.getState()!=ReportState.DECIDED || !Objects.equals(report.getLatestDecisionId(),request.decisionId()))
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT);
        SafetyDecision decision=decisions.findById(request.decisionId()).orElseThrow();
        var now=SafetySupport.now();
        if(!Set.of(Action.WARN,Action.RESTRICT).contains(decision.getAction()) || !now.isBefore(decision.getCreatedAt().plusDays(policy.appealDays())))
            throw new GeneralException(GeneralErrorCode.REPORT_CONFLICT,"Decision cannot be appealed or appeal period expired.");
        SafetyAppeal appeal=appeals.saveAndFlush(new SafetyAppeal(reportId,decision.getId(),userId,request.explanation().strip(),key,now));
        report.appeal(now);report.retainThrough(now.plusDays(policy.retentionDays()));reports.flush();
        log.info("Safety appeal received reportId={} appealId={}",reportId,appeal.getId());
        return AppealView.from(appeal);
    }
    public Page<AppealView> appeals(Long userId,int page,int size) {
        return appeals.findByUserId(userId,AdminMemberService.pageable(page,size)).map(AppealView::from);
    }
    public Page<NoticeView> notices(Long userId,int page,int size) {
        return notices.findByUserId(userId,AdminMemberService.pageable(page,size)).map(NoticeView::from);
    }
    @Transactional
    public NoticeView readNotice(Long userId,Long id) {
        SafetyNotice notice=notices.findForUpdate(id,userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.API_NOT_FOUND));
        notice.read(SafetySupport.now());return NoticeView.from(notice);
    }
    public List<SanctionView> sanctions(Long userId,int page,int size) {
        var now=SafetySupport.now();
        return sanctions.findByUserIdOrderByIdDesc(userId,AdminMemberService.pageable(page,size)).stream().map(s -> SanctionView.from(s,now)).toList();
    }
    public Permission permission(UUID userUuid) {
        User user=users.findByUuid(userUuid).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        boolean allowed=user.isAiWorkAllowed(SafetySupport.now());
        return new Permission(userUuid,user.isAccessAllowed(),allowed,allowed,user.getSafetyRestrictedUntil());
    }
}
