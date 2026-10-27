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
@RestController @RequestMapping("/support-tickets") @RequiredArgsConstructor
public class MemberSupportController {
 private final SupportTicketService tickets;
 private final SupportAttachmentService attachments;
 @PostMapping public ApiResponse<TicketView> create(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody Create c) {return ApiResponse.onSuccess("문의를 접수했습니다.",tickets.create(u.getId(),c));}
 @GetMapping public ApiResponse<Page<TicketView>> list(@AuthenticationPrincipal CustomUserDetails u,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {return ApiResponse.onSuccess("내 문의 목록입니다.",tickets.list(u.getId(),false,null,null,null,null,null,page,size));}
 @GetMapping("/{id}") public ApiResponse<MemberDetail> detail(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id) {return ApiResponse.onSuccess("문의 상세입니다.",tickets.member(u.getId(),id));}
 @PostMapping("/{id}/messages") public ApiResponse<PublicEntry> message(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Message c) {return ApiResponse.onSuccess("추가 문의를 기록했습니다.",tickets.customerMessage(u.getId(),id,c));}
 @PostMapping("/{id}/attachments") public ApiResponse<UploadView> upload(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Upload c) {return ApiResponse.onSuccess("첨부 업로드 링크입니다.",attachments.upload(u.getId(),id,c));}
 @PostMapping("/{id}/attachments/{attachmentId}/confirm") public ApiResponse<AttachmentView> confirm(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@PathVariable Long attachmentId) {return ApiResponse.onSuccess("첨부를 확정했습니다.",attachments.confirm(u.getId(),id,attachmentId));}
 @PostMapping("/{id}/attachments/{attachmentId}/access") public ApiResponse<Download> download(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@PathVariable Long attachmentId) {return ApiResponse.onSuccess("첨부 다운로드 링크입니다.",attachments.download(u.getId(),id,attachmentId));}
}
