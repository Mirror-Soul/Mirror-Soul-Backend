package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.domain.enums.UserRole;
import com.mirrorsoul.mirrorsoul_api.repository.UserRepository;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class SupportTicketService {
 private final SupportTicketRepository tickets;
 private final SupportEntryRepository entries;
 private final SupportRetryRepository retries;
 private final SupportAttachmentRepository attachments;
 private final UserRepository users;
 private final SupportAccess access;
 private final SupportDiagnostics diagnostics;
 public TicketView create(Long member,Create c) {return intake(member,c,Channel.APP,null);}
 public TicketView email(Long actor,EmailIntake c) {access.operator(actor);return intake(c.memberId(),c.ticket(),Channel.EMAIL,c.externalMessageId());}
 private TicketView intake(Long member,Create c,Channel channel,String externalId) {
  users.findByIdForUpdate(member).orElseThrow(()->new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
  String hash=SupportAccess.hash(channel,c.category(),c.subject(),c.body(),c.transactionId(),c.callId(),c.jobType(),c.jobId(),externalId);
  var prior=tickets.findByMemberIdAndRequestKey(member,SupportAccess.key(c.idempotencyKey()));
  if(prior.isEmpty()&&externalId!=null) prior=tickets.findByExternalMessageId(SupportAccess.hash(externalId));
  if(prior.isPresent()) {if(!Objects.equals(prior.get().getMemberId(),member)||!hash.equals(prior.get().getRequestHash())) idem();return view(prior.get());}
  if(tickets.countByMemberIdAndCreatedAtAfter(member,SupportAccess.now().minusDays(1))>=20) throw new GeneralException(GeneralErrorCode.SUPPORT_RATE_LIMITED);
  diagnostics.validate(member,c);
  var t=tickets.saveAndFlush(new SupportTicket(member,channel,c,hash,externalId,SupportAccess.now()));
  entries.save(new SupportEntry(t.getId(),member,EntryType.CUSTOMER_MESSAGE,c.body(),"initial",hash,SupportAccess.now()));
  return view(t);
 }
 @Transactional(readOnly=true) public Page<TicketView> list(Long actor,boolean admin,Status status,Category category,Channel channel,Long assignee,Long member,int page,int size) {
  return list(actor,admin,status,category,channel,assignee,member,page,size,
   com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow.all(),false);
 }
 @Transactional(readOnly=true) public Page<TicketView> list(Long actor,boolean admin,Status status,Category category,Channel channel,Long assignee,Long member,int page,int size,
  com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow window,boolean awaitingReply) {
  if(admin) access.reader(actor);
  Specification<SupportTicket> spec=com.mirrorsoul.mirrorsoul_api.common.query.AdminQueueFilters.support(window,awaitingReply).and((root,q,b)-> {var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
   if(!admin||member!=null) conditions.add(b.equal(root.get("memberId"),admin?member:actor));
   if(status!=null) conditions.add(b.equal(root.get("status"),status));if(category!=null) conditions.add(b.equal(root.get("category"),category));
   if(channel!=null) conditions.add(b.equal(root.get("channel"),channel));if(assignee!=null) conditions.add(b.equal(root.get("assigneeId"),assignee));
   return b.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));});
  return tickets.findAll(spec,SupportAccess.page(page,size)).map(this::view);
 }
 @Transactional(readOnly=true) public MemberDetail member(Long actor,Long id) {
  var t=ticket(id);access.owner(actor,t);
  return new MemberDetail(view(t),entries.findByTicketIdAndTypeInOrderByIdAsc(id,List.of(EntryType.CUSTOMER_MESSAGE,EntryType.REPLY)).stream().map(e->new PublicEntry(e.getId(),e.getType(),e.getBody(),e.getCreatedAt())).toList(),attachmentViews(id));
 }
 @Transactional(readOnly=true) public AdminDetail admin(Long actor,Long id) {
  access.reader(actor);var t=ticket(id);
  return new AdminDetail(view(t),entries.findByTicketIdOrderByIdAsc(id).stream().map(this::entry).toList(),attachmentViews(id),diagnostics.view(t));
 }
 public TicketView assign(Long actor,Long id,Assign input) {
  var u=access.operator(actor);var t=lock(id);SupportAccess.version(t,input.version());access.operator(input.assigneeId());
  if(u.getRole()!=UserRole.SUPER_ADMIN&&!Objects.equals(actor,input.assigneeId())) SupportAccess.forbidden();
  if(u.getRole()!=UserRole.SUPER_ADMIN&&t.getAssigneeId()!=null&&!Objects.equals(t.getAssigneeId(),actor)) SupportAccess.forbidden();
  t.assign(input.assigneeId(),SupportAccess.now());audit(t,actor,EntryType.ASSIGNMENT,"ASSIGNED:"+input.assigneeId());tickets.flush();return view(t);
 }
 public TicketView status(Long actor,Long id,Update input) {
  var t=lock(id);access.writer(actor,t);SupportAccess.version(t,input.version());
  if(t.getStatus()==Status.CLOSED&&input.status()!=Status.IN_PROGRESS) throw SupportAccess.conflict();
  if(input.status()==Status.RECEIVED) throw SupportAccess.conflict();
  if(t.getStatus()!=input.status()) {t.status(input.status(),SupportAccess.now());audit(t,actor,EntryType.STATUS_CHANGE,input.status().name());tickets.flush();}
  return view(t);
 }
 public AdminEntry message(Long actor,Long id,Message input,EntryType type) {
  var t=lock(id);if(type==EntryType.CUSTOMER_MESSAGE) access.owner(actor,t);else access.writer(actor,t);
  String hash=SupportAccess.hash(actor,type,input.body());String key="msg-"+SupportAccess.key(input.idempotencyKey());
  var prior=entries.findByTicketIdAndRequestKey(id,key);if(prior.isPresent()) {if(!hash.equals(prior.get().getRequestHash())) idem();return entry(prior.get());}
  SupportAccess.version(t,input.version());if(t.getStatus()==Status.CLOSED) throw SupportAccess.conflict();
  var result=entries.saveAndFlush(new SupportEntry(id,actor,type,input.body(),key,hash,SupportAccess.now()));
  if(type==EntryType.REPLY) t.status(Status.WAITING_CUSTOMER,SupportAccess.now());
  else if(type==EntryType.CUSTOMER_MESSAGE) t.customerMessage(SupportAccess.now());else t.touch(SupportAccess.now());
  tickets.flush();return entry(result);
 }
 public PublicEntry customerMessage(Long actor,Long id,Message c) {var e=message(actor,id,c,EntryType.CUSTOMER_MESSAGE);return new PublicEntry(e.id(),e.type(),e.body(),e.createdAt());}
 public AdminEntry retry(Long actor,Long id,Long entryId,Retry input) {
  var t=lock(id);access.writer(actor,t);var e=entries.lock(entryId).orElseThrow(SupportAccess::missing);
  if(!id.equals(e.getTicketId())||e.getType()!=EntryType.REPLY) throw SupportAccess.missing();
  var prior=retries.findByEntryIdAndRequestKey(entryId,SupportAccess.key(input.idempotencyKey()));
  if(prior.isPresent()) {if(!Objects.equals(prior.get().getAuthorId(),actor)||prior.get().isAcknowledged()!=input.acknowledgePossibleDuplicate()) idem();return entry(e);}
  if(e.getDelivery()!=Delivery.FAILED&&e.getDelivery()!=Delivery.UNKNOWN) throw SupportAccess.conflict();
  if(e.getDelivery()==Delivery.UNKNOWN&&!input.acknowledgePossibleDuplicate()) throw SupportAccess.conflict();
  retries.save(new SupportRetry(entryId,actor,input));e.retry();return entry(e);
 }
 private void audit(SupportTicket t,Long actor,EntryType type,String body) {entries.save(new SupportEntry(t.getId(),actor,type,body,"audit-"+UUID.randomUUID(),SupportAccess.hash(body),SupportAccess.now()));}
 SupportTicket ticket(Long id) {return tickets.findById(id).orElseThrow(SupportAccess::missing);}
 SupportTicket lock(Long id) {return tickets.lock(id).orElseThrow(SupportAccess::missing);}
 private void idem() {throw new GeneralException(GeneralErrorCode.SUPPORT_IDEMPOTENCY_CONFLICT);}
 private List<AttachmentView> attachmentViews(Long id) {return attachments.findByTicketIdAndConfirmedTrueAndDeletedFalseAndExpiresAtAfter(id,SupportAccess.now()).stream().map(a->new AttachmentView(a.getId(),a.getFileName(),a.getContentType(),a.getSize(),a.getExpiresAt())).toList();}
 TicketView view(SupportTicket t) {return new TicketView(t.getId(),t.getMemberId(),t.getChannel(),t.getCategory(),t.getStatus(),t.getSubject(),t.getAssigneeId(),t.getWaitingSince(),t.getCreatedAt(),t.getUpdatedAt(),t.getTransactionId(),t.getCallId(),t.getJobType(),t.getJobId(),t.getVersion());}
 AdminEntry entry(SupportEntry e) {return new AdminEntry(e.getId(),e.getType(),e.getBody(),e.getAuthorId(),e.getDelivery(),e.getFailureCode(),e.getAttempts(),e.getCreatedAt());}
}
