package com.mirrorsoul.mirrorsoul_api.access;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.support.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({SupportTicketService.class,SupportAccess.class,SupportDiagnostics.class,AdminAuditConfiguration.class,AdminAuditWriter.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminAuditConsistencyTest {
 @Autowired UserRepository users;@Autowired SupportTicketService tickets;@Autowired SupportTicketRepository repository;@Autowired AdminAuditEventRepository audit;
 User user() {return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash").status(UserStatus.ACTIVE).role(UserRole.USER).createdAt(LocalDateTime.now()).build());}
 @Test void businessChangeAndAuditCommitTogether() {
  var u=user();String correlation=UUID.randomUUID().toString();AdminAuditContext.set(new AdminAuditContext.Context(u.getId(),"EMAIL_INTAKE","tickets",correlation));
  try {var t=tickets.create(u.getId(),new SupportDtos.Create(SupportTypes.Category.OTHER,"subject","private content",null,null,null,null,"key"));
   assertThat(repository.findById(t.id())).isPresent();assertThat(audit.findAll().stream().filter(e->e.getCorrelationId().equals(correlation)).count()).isEqualTo(1);
  } finally {AdminAuditContext.clear();}
 }
 @Test void auditFailureRollsBackBusinessChange() {
  var u=user();AdminAuditContext.set(new AdminAuditContext.Context(u.getId(),"X".repeat(121),"tickets",UUID.randomUUID().toString()));
  try {assertThatThrownBy(()->tickets.create(u.getId(),new SupportDtos.Create(SupportTypes.Category.OTHER,"subject","private content",null,null,null,null,"key"))).isInstanceOf(RuntimeException.class);
   assertThat(repository.findAll().stream().filter(t->t.getMemberId().equals(u.getId())).count()).isZero();
  } finally {AdminAuditContext.clear();}
 }
}
