package com.mirrorsoul.mirrorsoul_api.support;
import static org.assertj.core.api.Assertions.*;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"},showSql=false)
@Import({SupportTicketService.class,SupportAccess.class,SupportDiagnostics.class,SupportDeliveryService.class})
class SupportWorkflowTest {
 @Autowired UserRepository users;
 @Autowired SupportTicketService service;
 @Autowired SupportTicketRepository tickets;
 @Autowired SupportEntryRepository entries;
 @Autowired SupportRetryRepository retries;
 @Autowired SupportDeliveryService delivery;
 User user(UserRole role) {return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com").passwordHash("hash").status(UserStatus.ACTIVE).role(role).createdAt(LocalDateTime.now()).build());}
 Create request(String key) {return new Create(Category.CALL,"통화 문의","private customer text",null,null,null,null,key);}
 TicketView assigned(User member,User admin) {var t=service.create(member.getId(),request("initial"));return service.assign(admin.getId(),t.id(),new Assign(admin.getId(),t.version()));}
 @Test void intakeIsIdempotentAndPrivateByOwner() {
  var member=user(UserRole.USER);var other=user(UserRole.USER);
  var t=service.create(member.getId(),request("create-one"));assertThat(service.create(member.getId(),request("create-one"))).isEqualTo(t);
  assertThatThrownBy(()->service.create(member.getId(),new Create(Category.OTHER,"different","different",null,null,null,null,"create-one"))).isInstanceOf(GeneralException.class);
  assertThat(service.list(member.getId(),false,null,null,null,null,null,0,20).getTotalElements()).isEqualTo(1);assertThat(entries.findByTicketIdOrderByIdAsc(t.id())).hasSize(1);
  assertThatThrownBy(()->service.member(other.getId(),t.id())).isInstanceOf(GeneralException.class);
  assertThat(service.list(other.getId(),false,null,null,null,null,null,0,20)).isEmpty();
 }
 @Test void internalNotesAreExcludedAndRepeatedNotesAdvanceVersionWithinSameSecond() {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);var t=assigned(member,admin);
  service.message(admin.getId(),t.id(),new Message("secret note",t.version(),"note-one"),EntryType.NOTE);
  var newer=service.admin(admin.getId(),t.id()).ticket();assertThat(newer.version()).isGreaterThan(t.version());
  var reply=service.message(admin.getId(),t.id(),new Message("public reply",newer.version(),"reply-one"),EntryType.REPLY);
  assertThat(service.message(admin.getId(),t.id(),new Message("public reply",newer.version(),"reply-one"),EntryType.REPLY).id()).isEqualTo(reply.id());
  var publicView=service.member(member.getId(),t.id());assertThat(publicView.messages()).extracting(PublicEntry::body).containsExactly("private customer text","public reply");
  assertThat(publicView.ticket().status()).isEqualTo(Status.WAITING_CUSTOMER);assertThat(publicView.ticket().waitingSince()).isNull();
  assertThat(service.admin(admin.getId(),t.id()).history()).extracting(AdminEntry::body).contains("secret note");
  service.customerMessage(member.getId(),t.id(),new Message("followup",publicView.ticket().version(),"followup-one"));
  assertThat(service.member(member.getId(),t.id()).ticket().waitingSince()).isNotNull();
 }
 @Test void viewerCannotWriteAndOtherOperatorCannotTakeAssignedTicket() {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);var viewer=user(UserRole.ADMIN_VIEWER);var other=user(UserRole.ADMIN);var t=assigned(member,admin);
  assertThat(service.admin(viewer.getId(),t.id()).ticket().id()).isEqualTo(t.id());
  assertThatThrownBy(()->service.message(viewer.getId(),t.id(),new Message("note",t.version(),"key"),EntryType.NOTE)).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.assign(other.getId(),t.id(),new Assign(other.getId(),t.version()))).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.message(admin.getId(),t.id(),new Message("reply",0L,"key"),EntryType.REPLY)).isInstanceOf(GeneralException.class);
 }
 @Test void emailIntakeDeduplicatesProviderMessageAndRejectsForeignContext() {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);
  var t=service.email(admin.getId(),new EmailIntake(member.getId(),"provider-message-1",request("email-one")));
  var replay=service.email(admin.getId(),new EmailIntake(member.getId(),"provider-message-1",request("email-two")));
  assertThat(replay.id()).isEqualTo(t.id());assertThat(t.channel()).isEqualTo(Channel.EMAIL);
  assertThatThrownBy(()->service.create(member.getId(),new Create(Category.CALL,"call","body",null,999L,null,null,"foreign"))).isInstanceOf(GeneralException.class);
  assertThatThrownBy(()->service.create(member.getId(),new Create(Category.TWIN,"job","body",null,null,JobType.VOICE,null,"incomplete"))).isInstanceOf(GeneralException.class);
 }
 @Test void deliveryClaimsOnlyOnceAndRetryKeyCannotScheduleDuplicate() {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);var t=assigned(member,admin);
  var e=service.message(admin.getId(),t.id(),new Message("answer",t.version(),"reply"),EntryType.REPLY);
  var attempt=delivery.claim(e.id(),true);assertThat(attempt).isNotNull();assertThat(delivery.claim(e.id(),true)).isNull();
  delivery.finish(attempt,Delivery.UNKNOWN,"DELIVERY_UNCERTAIN");
  assertThatThrownBy(()->service.retry(admin.getId(),t.id(),e.id(),new Retry("retry",false))).isInstanceOf(GeneralException.class);
  service.retry(admin.getId(),t.id(),e.id(),new Retry("retry",true));
  var again=delivery.claim(e.id(),true);assertThat(again).isNotNull();
  service.retry(admin.getId(),t.id(),e.id(),new Retry("retry",true));assertThat(delivery.claim(e.id(),true)).isNull();
  delivery.finish(attempt,Delivery.SENT,null);assertThat(entries.findById(e.id()).orElseThrow().getDelivery()).isEqualTo(Delivery.SENDING);
  delivery.finish(again,Delivery.SENT,null);assertThat(entries.findById(e.id()).orElseThrow().getDelivery()).isEqualTo(Delivery.SENT);assertThat(retries.count()).isEqualTo(1);
 }
 @Test void mailConfigurationFailureDoesNotRemoveInboxAnswer() {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);var t=assigned(member,admin);
  var e=service.message(admin.getId(),t.id(),new Message("answer",t.version(),"reply"),EntryType.REPLY);
  assertThat(delivery.claim(e.id(),false)).isNull();assertThat(entries.findById(e.id()).orElseThrow().getFailureCode()).isEqualTo("MAIL_NOT_CONFIGURED");
  assertThat(service.member(member.getId(),t.id()).messages()).extracting(PublicEntry::body).contains("answer");
 }
 @Test void closedTicketRejectsNewMessagesAndRequiresExplicitReopen() {
  var member=user(UserRole.USER);var admin=user(UserRole.ADMIN);var t=assigned(member,admin);
  var closed=service.status(admin.getId(),t.id(),new Update(Status.CLOSED,t.version()));
  assertThatThrownBy(()->service.customerMessage(member.getId(),t.id(),new Message("body",closed.version(),"closed"))).isInstanceOf(GeneralException.class);
  assertThat(service.status(admin.getId(),t.id(),new Update(Status.IN_PROGRESS,closed.version())).status()).isEqualTo(Status.IN_PROGRESS);
 }
}
