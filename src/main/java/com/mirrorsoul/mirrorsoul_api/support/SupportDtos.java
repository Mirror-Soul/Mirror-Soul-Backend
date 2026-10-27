package com.mirrorsoul.mirrorsoul_api.support;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
public final class SupportDtos {
 private SupportDtos() {}
 public record Create(@NotNull Category category,@NotBlank @Size(max=200) String subject,
  @NotBlank @Size(max=10000) String body,Long transactionId,Long callId,JobType jobType,Long jobId,
  @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
 public record EmailIntake(@NotNull Long memberId,@NotBlank @Size(max=200) String externalMessageId,@NotNull @jakarta.validation.Valid Create ticket) {}
 public record Message(@NotBlank @Size(max=10000) String body,@NotNull @PositiveOrZero Long version,
  @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
 public record Update(@NotNull Status status,@NotNull @PositiveOrZero Long version) {}
 public record Assign(@NotNull Long assigneeId,@NotNull @PositiveOrZero Long version) {}
 public record Retry(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey,
  boolean acknowledgePossibleDuplicate) {}
 public record TicketView(Long id,Long memberId,Channel channel,Category category,Status status,String subject,
  Long assigneeId,LocalDateTime waitingSince,LocalDateTime createdAt,LocalDateTime updatedAt,
  Long transactionId,Long callId,JobType jobType,Long jobId,Long version) {}
 public record PublicEntry(Long id,EntryType type,String body,LocalDateTime createdAt) {}
 public record AdminEntry(Long id,EntryType type,String body,Long authorId,Delivery delivery,String failureCode,
  int attempts,LocalDateTime createdAt) {}
 public record MemberDetail(TicketView ticket,List<PublicEntry> messages,List<AttachmentView> attachments) {}
 public record AdminDetail(TicketView ticket,List<AdminEntry> history,List<AttachmentView> attachments,Diagnostic diagnostic) {}
 public record Diagnostic(Long callId,List<String> callCodes,JobType jobType,Long jobId,String jobStatus,String safeErrorCode) {}
 public record AttachmentView(Long id,String fileName,String contentType,long size,LocalDateTime expiresAt) {}
 public record Upload(@NotBlank @Size(max=150) String fileName,@NotBlank String contentType,@Min(1) @Max(5242880) long size) {}
 public record UploadView(Long id,String url,java.util.Map<String,java.util.List<String>> signedHeaders,LocalDateTime uploadExpiresAt,LocalDateTime expiresAt) {}
 public record Download(String url,java.util.Map<String,java.util.List<String>> signedHeaders,LocalDateTime expiresAt) {}
}
