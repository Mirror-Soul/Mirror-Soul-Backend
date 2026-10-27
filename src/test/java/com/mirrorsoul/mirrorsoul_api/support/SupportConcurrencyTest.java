package com.mirrorsoul.mirrorsoul_api.support;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({SupportTicketService.class,SupportAccess.class,SupportDiagnostics.class,SupportDeliveryService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class SupportConcurrencyTest {
 @Autowired UserRepository users;
 @Autowired SupportTicketService tickets;
 @Autowired SupportEntryRepository entries;
 @Autowired SupportDeliveryService delivery;
 User user(UserRole role) {return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash").status(UserStatus.ACTIVE).role(role).createdAt(LocalDateTime.now()).build());}
 @Test void concurrentIntakeReplyAndDispatchClaimDoNotDuplicate() throws Exception {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);var pool=Executors.newFixedThreadPool(2);
  try {
   var c=new Create(Category.OTHER,"subject","body",null,null,null,null,"same-key");var ready=new CountDownLatch(2);
   java.util.function.Supplier<TicketView> submit=()-> {await(ready);return tickets.create(member.getId(),c);};
   var a=CompletableFuture.supplyAsync(submit,pool);var b=CompletableFuture.supplyAsync(submit,pool);var t=a.get(15,TimeUnit.SECONDS);assertThat(b.get(15,TimeUnit.SECONDS).id()).isEqualTo(t.id());
   var assigned=tickets.assign(admin.getId(),t.id(),new Assign(admin.getId(),t.version()));var repliesReady=new CountDownLatch(2);
   java.util.function.Supplier<AdminEntry> reply=()-> {await(repliesReady);return tickets.message(admin.getId(),t.id(),new Message("answer",assigned.version(),"same-reply"),EntryType.REPLY);};
   var first=CompletableFuture.supplyAsync(reply,pool);var second=CompletableFuture.supplyAsync(reply,pool);var e=first.get(15,TimeUnit.SECONDS);assertThat(second.get(15,TimeUnit.SECONDS).id()).isEqualTo(e.id());
   assertThat(entries.findByTicketIdOrderByIdAsc(t.id()).stream().filter(x->x.getType()==EntryType.REPLY).count()).isEqualTo(1);
   var deliveryReady=new CountDownLatch(2);java.util.function.Supplier<SupportDeliveryService.Attempt> claim=()-> {await(deliveryReady);return delivery.claim(e.id(),true);};
   var one=CompletableFuture.supplyAsync(claim,pool);var two=CompletableFuture.supplyAsync(claim,pool);
   assertThat(Arrays.asList(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS)).stream().filter(Objects::nonNull).count()).isEqualTo(1);
  } finally {pool.shutdownNow();}
 }
 static void await(CountDownLatch ready) {ready.countDown();try {if(!ready.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");}catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
