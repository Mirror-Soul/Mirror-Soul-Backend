package com.mirrorsoul.mirrorsoul_api.safety;

import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({SafetyReportService.class,SafetyDecisionService.class,SafetyMemberService.class,SafetyEvidenceService.class,SafetyAuditWriter.class,SafetyWorkflowTest.Config.class})
class SafetyWorkflowTest {
    @Autowired UserRepository users;
    @Autowired SafetyReportRepository reportRepository;
    @Autowired SafetyDecisionRepository decisionRepository;
    @Autowired SafetyNoticeRepository notices;
    @Autowired SafetyEvidenceAuditRepository audits;
    @Autowired SafetyReportService reports;
    @Autowired SafetyDecisionService decisions;
    @Autowired SafetyMemberService members;
    @Autowired SafetyEvidenceService evidence;
    @org.springframework.boot.test.context.TestConfiguration static class Config {
        @Bean SafetyPolicy safetyPolicy() { return new SafetyPolicy(10,50,90,7,14); }
    }
    private User user(UserRole role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash")
            .status(UserStatus.ACTIVE).role(role).createdAt(LocalDateTime.now()).build());
    }
    private Submit submit(User target,String key) { return new Submit(target.getUuid(),ReportType.THREATS,"Private evidence",null,null,key); }
    private ReportView assigned(User reporter,User target,User admin,String key) {
        ReportView view=reports.submit(reporter.getId(),submit(target,key));
        return reports.assign(admin.getId(),view.id(),new Assign(admin.getId(),view.version()));
    }
    private Decide restriction(ReportView r,User target,String key,Integer days) {
        return new Decide(Action.RESTRICT,Reason.VIOLATION_CONFIRMED,"이용 정책 위반으로 신규 이용이 제한됩니다.",r.version(),target.getVersion(),days,key);
    }
    @Test void duplicateSubmissionsAndDecisionsReturnOriginalResultsWithoutDuplicateNotices() {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN);
        var input=submit(target,"REQUEST-1");
        var first=reports.submit(reporter.getId(),input);
        assertThat(reports.submit(reporter.getId(),input)).isEqualTo(first);
        assertThat(first.priority()).isEqualTo(Priority.URGENT);
        assertThatThrownBy(() -> reports.submit(reporter.getId(),new Submit(target.getUuid(),ReportType.THREATS,"changed",null,null,"request-1")))
            .isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> reports.submit(reporter.getId(),submit(target,"other-key"))).isInstanceOf(GeneralException.class);
        var assigned=reports.assign(admin.getId(),first.id(),new Assign(admin.getId(),first.version()));
        var request=restriction(assigned,target,"DECISION-1",null);
        var result=decisions.decide(admin.getId(),first.id(),request);
        assertThat(decisions.decide(admin.getId(),first.id(),request)).isEqualTo(result);
        assertThat(notices.count()).isEqualTo(2);
        assertThat(target.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(target.isAccessAllowed()).isTrue();
        assertThat(target.isAiWorkAllowed(LocalDateTime.now())).isFalse();
        assertThatThrownBy(() -> decisions.decide(admin.getId(),first.id(),new Decide(Action.RESTRICT,Reason.VIOLATION_CONFIRMED,
            "different",assigned.version(),0L,null,"decision-1"))).isInstanceOf(GeneralException.class);
    }
    @Test void staleVersionAndUnassignedOrViewerDecisionsAreRejected() {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN),other=user(UserRole.ADMIN),viewer=user(UserRole.ADMIN_VIEWER);
        var r=assigned(reporter,target,admin,"report-stale");
        assertThatThrownBy(() -> decisions.decide(other.getId(),r.id(),restriction(r,target,"wrong-actor",7))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> decisions.decide(viewer.getId(),r.id(),restriction(r,target,"viewer",7))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> decisions.decide(admin.getId(),r.id(),new Decide(Action.RESTRICT,Reason.VIOLATION_CONFIRMED,"reason",0L,target.getVersion(),7,"stale")))
            .isInstanceOf(GeneralException.class);
        assertThat(decisionRepository.count()).isZero();
        assertThat(target.getSafetyRestrictedUntil()).isNull();
    }
    @Test void releasingOneReportDoesNotRemoveAnotherActiveRestriction() {
        User first=user(UserRole.USER),second=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN);
        var r1=assigned(first,target,admin,"one");var d1=decisions.decide(admin.getId(),r1.id(),restriction(r1,target,"restrict-one",7));
        var r2=assigned(second,target,admin,"two");decisions.decide(admin.getId(),r2.id(),restriction(r2,target,"restrict-two",14));
        var deadline=target.getSafetyRestrictedUntil();
        decisions.decide(admin.getId(),r1.id(),new Decide(Action.RELEASE,Reason.RESTRICTION_COMPLETED,"제한 해제",d1.reportVersion(),target.getVersion(),null,"release-one"));
        assertThat(target.getSafetyRestrictedUntil()).isEqualTo(deadline);
        assertThat(target.isAiWorkAllowed(LocalDateTime.now())).isFalse();
    }
    @Test void appealCanBeFiledWhileRestrictedAndRequiresAnotherReviewer() {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN),reviewer=user(UserRole.ADMIN);
        var r=assigned(reporter,target,admin,"report-appeal");var result=decisions.decide(admin.getId(),r.id(),restriction(r,target,"restrict",7));
        var appeal=new Appeal(result.id(),"제재 사유를 재검토해 주세요.","appeal-1");
        var a=members.appeal(target.getId(),r.id(),appeal);
        assertThat(members.appeal(target.getId(),r.id(),appeal)).isEqualTo(a);
        assertThatThrownBy(() -> members.appeal(reporter.getId(),r.id(),appeal)).isInstanceOf(GeneralException.class);
        var pending=reportRepository.findById(r.id()).orElseThrow();
        assertThatThrownBy(() -> reports.assign(admin.getId(),r.id(),new Assign(admin.getId(),pending.getVersion()))).isInstanceOf(GeneralException.class);
        var assigned=reports.assign(reviewer.getId(),r.id(),new Assign(reviewer.getId(),pending.getVersion()));
        assertThat(assigned.state()).isEqualTo(ReportState.APPEALED);
        decisions.decide(reviewer.getId(),r.id(),new Decide(Action.APPROVE_APPEAL,Reason.APPEAL_ACCEPTED,"이의 제기를 수용합니다.",assigned.version(),target.getVersion(),null,"accept"));
        assertThat(target.isAiWorkAllowed(LocalDateTime.now())).isTrue();
        assertThat(members.appeals(target.getId(),0,20).getContent().get(0).state()).isEqualTo(AppealState.ACCEPTED);
    }
    @Test void onlyAssignedAdministratorsCanReadEvidenceAndDeniedAccessIsAudited() {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN),other=user(UserRole.ADMIN);
        var r=assigned(reporter,target,admin,"evidence-report");
        assertThatThrownBy(() -> evidence.access(other.getId(),r.id(),"REPORT_REVIEW")).isInstanceOf(GeneralException.class);
        assertThat(audits.findByReportId(r.id(),org.springframework.data.domain.PageRequest.of(0,20)).getContent()).anyMatch(a -> !a.isAllowed());
        assertThat(evidence.access(admin.getId(),r.id(),"REPORT_REVIEW").description()).isEqualTo("Private evidence");
        assertThatThrownBy(() -> reports.mine(target.getId(),r.id())).isInstanceOf(GeneralException.class);
    }
    @Test void expiredEvidenceIsPurgedAndCannotBeRead() {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN);
        var old=reportRepository.saveAndFlush(new SafetyReport(reporter.getId(),target.getId(),ReportType.OTHER,Priority.NORMAL,null,null,
            "private","snapshot","old","hash","context",LocalDateTime.now().minusDays(91),90));
        reports.assign(admin.getId(),old.getId(),new Assign(admin.getId(),old.getVersion()));
        assertThatThrownBy(() -> evidence.access(admin.getId(),old.getId(),"REPORT_REVIEW")).isInstanceOf(GeneralException.class);
        assertThat(evidence.purgeExpired()).isEqualTo(1);
        assertThat(old.getDescription()).isNull();assertThat(old.getMessageSnapshot()).isNull();assertThat(old.isEvidencePurged()).isTrue();
    }
    @Test void noticeReadCannotAccessAnotherMembersInbox() {
        User reporter=user(UserRole.USER),target=user(UserRole.USER),admin=user(UserRole.ADMIN);
        var r=assigned(reporter,target,admin,"notice");var decision=decisions.decide(admin.getId(),r.id(),restriction(r,target,"notice-decision",7));
        assertThatThrownBy(() -> members.readNotice(reporter.getId(),decision.targetNoticeId())).isInstanceOf(GeneralException.class);
        assertThat(members.readNotice(target.getId(),decision.targetNoticeId()).readAt()).isNotNull();
    }
}
