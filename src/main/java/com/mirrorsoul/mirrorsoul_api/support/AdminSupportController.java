package com.mirrorsoul.mirrorsoul_api.support;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.ApiResponse;
import com.mirrorsoul.mirrorsoul_api.common.security.CustomUserDetails;
import com.mirrorsoul.mirrorsoul_api.support.SupportDtos.*;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/admin/support-tickets") @RequiredArgsConstructor
public class AdminSupportController {
 private final SupportTicketService tickets;
 private final SupportAttachmentService attachments;
 @PostMapping("/email-intake") public ApiResponse<TicketView> email(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody EmailIntake c) {return ApiResponse.onSuccess("이메일 문의를 접수했습니다.",tickets.email(u.getId(),c));}
 @GetMapping public ApiResponse<Page<TicketView>> list(@AuthenticationPrincipal CustomUserDetails u,
 @RequestParam(required=false) Status status,@RequestParam(required=false) Category category,@RequestParam(required=false) Channel channel,
 @RequestParam(required=false) Long assigneeId,@RequestParam(required=false) Long memberId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
 @RequestParam(required=false) java.time.OffsetDateTime from,@RequestParam(required=false) java.time.OffsetDateTime to,
 @RequestParam(defaultValue="false") boolean awaitingReply) {return ApiResponse.onSuccess("고객문의 목록입니다.",tickets.list(u.getId(),true,status,category,channel,assigneeId,memberId,page,size,
  new com.mirrorsoul.mirrorsoul_api.common.query.AdminListWindow(from,to),awaitingReply));}
 @GetMapping("/{id}") public ApiResponse<AdminDetail> detail(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id) {return ApiResponse.onSuccess("고객문의 상세입니다.",tickets.admin(u.getId(),id));}
 @PatchMapping("/{id}/assignee") public ApiResponse<TicketView> assign(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Assign c) {return ApiResponse.onSuccess("담당자를 지정했습니다.",tickets.assign(u.getId(),id,c));}
 @PatchMapping("/{id}/status") public ApiResponse<TicketView> status(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Update c) {return ApiResponse.onSuccess("문의 상태를 변경했습니다.",tickets.status(u.getId(),id,c));}
 @PostMapping("/{id}/replies") public ApiResponse<AdminEntry> reply(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Message c) {return ApiResponse.onSuccess("답변을 기록하고 발송을 예약했습니다.",tickets.message(u.getId(),id,c,EntryType.REPLY));}
 @PostMapping("/{id}/notes") public ApiResponse<AdminEntry> note(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Message c) {return ApiResponse.onSuccess("내부 메모를 기록했습니다.",tickets.message(u.getId(),id,c,EntryType.NOTE));}
 @PostMapping("/{id}/replies/{replyId}/retry") public ApiResponse<AdminEntry> retry(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@PathVariable Long replyId,@Valid @RequestBody Retry c) {return ApiResponse.onSuccess("답변 재발송을 예약했습니다.",tickets.retry(u.getId(),id,replyId,c));}
 @PostMapping("/{id}/attachments/{attachmentId}/access") public ApiResponse<Download> download(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@PathVariable Long attachmentId) {return ApiResponse.onSuccess("제한된 첨부 다운로드 링크입니다.",attachments.download(u.getId(),id,attachmentId));}
}
