package com.mirrorsoul.mirrorsoul_api.repository;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.dto.member.*;
import com.mirrorsoul.mirrorsoul_api.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import(AdminMemberService.class)
class AdminMemberPersistenceTest {
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired AdminMemberService service;
    @MockitoBean MemberDeletionService deletion;
    private User save(String email,UserRole role,UserStatus status) {
        return users.saveAndFlush(User.builder().email(email).passwordHash("hash").role(role).status(status).createdAt(java.time.LocalDateTime.now()).build());
    }
    @Test void persistedActionIncrementsVersionAndReplayDoesNotChangeIt() {
        User admin=save("admin@example.com",UserRole.ADMIN,UserStatus.ACTIVE);
        User target=save("target@example.com",UserRole.USER,UserStatus.ONBOARD_B);
        var request=new MemberActionRequest(MemberAction.SUSPEND,MemberReasonCode.SECURITY_RISK,target.getVersion(),"key-1");
        var result=service.act(admin.getId(),target.getId(),request);
        assertThat(result.version()).isEqualTo(1L);
        em.clear();
        assertThat(service.act(admin.getId(),target.getId(),request)).isEqualTo(result);
        assertThat(service.history(admin.getId(),target.getId(),0,20).getTotalElements()).isEqualTo(1);
        assertThat(service.detail(admin.getId(),target.getId()).onboardingStage()).isEqualTo(UserStatus.ONBOARD_B);
    }
    @Test void searchEscapesWildcardsAndFiltersSuspendedOnboardingStage() {
        User admin=save("admin@example.com",UserRole.ADMIN,UserStatus.ACTIVE);
        User target=save("literal%_@example.com",UserRole.USER,UserStatus.ONBOARD_C);
        target.suspend();users.flush();em.clear();
        var result=service.list(admin.getId(),"%_","SUSPENDED",UserStatus.ONBOARD_C,0,20);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).maskedEmail()).isEqualTo("l***@example.com");
        assertThat(service.list(admin.getId(),target.getId().toString(),null,null,0,20).getTotalElements()).isEqualTo(1);
        assertThat(service.list(admin.getId(),target.getUuid().toString(),null,null,0,20).getTotalElements()).isEqualTo(1);
    }
}
