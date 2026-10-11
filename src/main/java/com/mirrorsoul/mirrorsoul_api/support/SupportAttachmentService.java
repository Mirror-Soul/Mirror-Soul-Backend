package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import com.mirrorsoul.mirrorsoul_api.config.AwsS3Properties;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import java.time.Duration;
import java.util.*;
@Service
public class SupportAttachmentService {
 private final SupportAttachmentRepository attachments;
 private final SupportTicketRepository tickets;
 private final SupportAccess access;
 private final S3Client s3;
 private final S3Presigner presigner;
 private final AwsS3Properties properties;
 private final int retentionDays;
 private final String bucket;
 public SupportAttachmentService(SupportAttachmentRepository attachments,SupportTicketRepository tickets,SupportAccess access,S3Client s3,S3Presigner presigner,AwsS3Properties properties,
 @Value("${support.attachments.retention-days:30}") int retentionDays,@Value("${support.attachments.bucket:}") String bucket) {
  if(retentionDays<1||retentionDays>365) throw new IllegalArgumentException("Invalid attachment retention");
  this.attachments=attachments;this.tickets=tickets;this.access=access;this.s3=s3;this.presigner=presigner;this.properties=properties;this.retentionDays=retentionDays;this.bucket=bucket.isBlank()?properties.getBucket():bucket;
 }
 @Transactional public UploadView upload(Long actor,Long ticketId,Upload input) {
  var t=tickets.lock(ticketId).orElseThrow(SupportAccess::missing);access.owner(actor,t);
  if(t.getStatus()==SupportTypes.Status.CLOSED||attachments.countByTicketIdAndDeletedFalse(ticketId)>=5) throw SupportAccess.conflict();
  if(!Set.of("image/jpeg","image/png","application/pdf").contains(input.contentType())) throw new GeneralException(GeneralErrorCode.UNSUPPORTED_FILE_TYPE);
  if(input.size()<1||input.size()>5242880) throw new GeneralException(GeneralErrorCode.FILE_TOO_LARGE);
  String key="support-private/"+ticketId+"/"+UUID.randomUUID();
  var a=attachments.saveAndFlush(new SupportAttachment(ticketId,actor,input,key,SupportAccess.now(),retentionDays));
  var signed=presigner.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5))
   .putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).contentType(input.contentType()).contentLength(input.size()).serverSideEncryption(ServerSideEncryption.AES256).build()).build());
  return new UploadView(a.getId(),signed.url().toString(),signed.signedHeaders(),a.getUploadExpiresAt(),a.getExpiresAt());
 }
 @Transactional public AttachmentView confirm(Long actor,Long ticketId,Long id) {
  var t=tickets.lock(ticketId).orElseThrow(SupportAccess::missing);access.owner(actor,t);
  var a=attachments.lock(id).orElseThrow(SupportAccess::missing);checkTicket(ticketId,a);
  if(!Objects.equals(actor,a.getOwnerId())) SupportAccess.forbidden();
  if(a.isDeleted()||!a.getExpiresAt().isAfter(SupportAccess.now())) throw SupportAccess.conflict();
  if(a.isConfirmed()) return view(a);
  // Pin the verified version/ETag; subsequent PUTs cannot substitute unverified content in downloads.
  var head=s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(a.getObjectKey()).build());
  if(head.contentLength()==null||head.contentLength()!=a.getSize()||!Objects.equals(head.contentType(),a.getContentType())||head.eTag()==null) throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
  var request=GetObjectRequest.builder().bucket(bucket).key(a.getObjectKey()).range("bytes=0-7").ifMatch(head.eTag());
  if(head.versionId()!=null) request.versionId(head.versionId());
  byte[] bytes=s3.getObjectAsBytes(request.build()).asByteArray();
  boolean valid=switch(a.getContentType()) {
   case "image/jpeg" -> bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
   case "image/png" -> bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
   case "application/pdf" -> bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");default -> false;
  };
  if(!valid) throw new GeneralException(GeneralErrorCode.UNSUPPORTED_FILE_TYPE);
  a.confirm(head.versionId(),head.eTag());return view(a);
 }
 @Transactional(readOnly=true) public Download download(Long actor,Long ticketId,Long id) {
  var t=tickets.findById(ticketId).orElseThrow(SupportAccess::missing);access.attachment(actor,t);
  var a=attachments.findById(id).orElseThrow(SupportAccess::missing);checkTicket(ticketId,a);
  if(!a.isConfirmed()||a.isDeleted()||!a.getExpiresAt().isAfter(SupportAccess.now())) throw SupportAccess.missing();
  long seconds=Math.min(300,Duration.between(SupportAccess.now(),a.getExpiresAt()).getSeconds());if(seconds<1) throw SupportAccess.missing();
  var request=GetObjectRequest.builder().bucket(bucket).key(a.getObjectKey()).ifMatch(a.getEtag())
   .responseContentDisposition("attachment; filename=support-attachment").responseContentType("application/octet-stream");
  if(a.getObjectVersion()!=null) request.versionId(a.getObjectVersion());
  var signed=presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofSeconds(seconds)).getObjectRequest(request.build()).build());
  return new Download(signed.url().toString(),signed.signedHeaders(),SupportAccess.now().plusSeconds(seconds));
 }
 @Transactional public void deleteExpired(Long id) {
  var a=attachments.lock(id).orElseThrow(SupportAccess::missing);if(a.isDeleted()||a.getExpiresAt().isAfter(SupportAccess.now())) return;
  if(a.getObjectVersion()!=null) s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(a.getObjectKey()).versionId(a.getObjectVersion()).build());
  s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(a.getObjectKey()).build());a.deleted();
 }
 private void checkTicket(Long ticket,SupportAttachment a) {if(!Objects.equals(ticket,a.getTicketId())) throw SupportAccess.missing();}
 private AttachmentView view(SupportAttachment a) {return new AttachmentView(a.getId(),a.getFileName(),a.getContentType(),a.getSize(),a.getExpiresAt());}
}
