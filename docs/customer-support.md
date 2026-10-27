# 고객문의 백엔드

자체 티켓을 사용한다. 외부 헬프데스크는 초기에는 선택하지 않았으며 별도 연동은 없다.
답변은 DB에 저장되는 즉시 회원 문의함에서 보이고 이메일은 별도로 발송한다. 내부 메모는 이메일 발송 대상과 회원 응답에서 제외된다.

## 정책

- 유형: ACCOUNT, BILLING, TWIN, CALL, SAFETY, OTHER. 접수 경로: APP, EMAIL.
- 상태: RECEIVED(접수), IN_PROGRESS(처리 중), WAITING_CUSTOMER(회원 답변 대기), RESOLVED(해결), CLOSED(종료).
- 운영자 배정 시 RECEIVED → IN_PROGRESS. 답변 등록 시 WAITING_CUSTOMER. 회원 추가 문의 시 IN_PROGRESS.
- CLOSED 티켓의 추가 문의·답변·메모·첨부 업로드는 거절한다. 담당자가 IN_PROGRESS로 명시적으로 재개할 수 있다.
- waitingSince는 운영자 답변을 기다리기 시작한 시각이다. 회원에게 답변하면 null로 바뀐다. 반복 회원 메시지는 최초 대기 시각을 유지한다.
- ADMIN은 자신에게 미배정 티켓을 배정하며 담당 티켓만 수정한다. SUPER_ADMIN은 다른 활성 운영자에게도 배정하고 모든 티켓을 처리할 수 있다.
- ADMIN_VIEWER는 관리자 목록·상세를 읽을 수 있으나 수정·발송·첨부 다운로드는 불가능하다.
- 회원은 자신의 티켓·고객 메시지·운영자 답변만 읽는다. 내부 메모, 담당자 조치 이력, 작성자 ID, 발송 오류는 회원 메시지 DTO에서 제외된다.
- 회원별 최근 24시간 신규 문의 20건. 목록은 0부터 시작하고 size는 1~100이다.
- 문의/메시지/재발송 멱등키는 1~100자의 영문·숫자·밑줄·하이픈이며 대소문자를 구분하지 않는다. 같은 키에 다른 내용을 보내면 SUPPORT_4091.
- 수정 요청의 version은 최신 ticket.version이다. 오래된 요청은 SUPPORT_4090. 같은 메시지 키 재시도는 version 검사 전에 기존 메시지를 반환한다.
- 추가 문의, 답변, 메모는 같은 초에 기록되어도 version이 증가한다. 재전송은 기존 답변을 수정하지 않고 별도 요청 기록을 남긴다.

## API

공통 인증은 기존 JWT이며 JSON 결과는 기존 ApiResponse 포맷이다.

| API | 기능 |
|---|---|
| POST /support-tickets | 앱 문의 접수 |
| GET /support-tickets | 본인 문의 목록 |
| GET /support-tickets/{id} | 본인 문의 상세·공개 메시지·첨부 |
| POST /support-tickets/{id}/messages | 회원 추가 문의 |
| POST /support-tickets/{id}/attachments | 첨부 PUT URL 발급 |
| POST /support-tickets/{id}/attachments/{attachmentId}/confirm | 업로드 검사·확정 |
| POST /support-tickets/{id}/attachments/{attachmentId}/access | 본인 첨부 GET URL |
| POST /admin/support-tickets/email-intake | 운영자가 이메일 접수 내용을 티켓으로 등록 |
| GET /admin/support-tickets | 상태·유형·경로·담당자·회원 필터 목록 |
| GET /admin/support-tickets/{id} | 관리자 상세·전체 이력·안전한 진단 |
| PATCH /admin/support-tickets/{id}/assignee | 담당자 배정 |
| PATCH /admin/support-tickets/{id}/status | 상태 변경 |
| POST /admin/support-tickets/{id}/replies | 회원용 답변 및 이메일 예약 |
| POST /admin/support-tickets/{id}/notes | 내부 메모 |
| POST /admin/support-tickets/{id}/replies/{replyId}/retry | 이메일 재발송 |
| POST /admin/support-tickets/{id}/attachments/{attachmentId}/access | 담당자 또는 SUPER_ADMIN 첨부 접근 |

문의 접수 예시:

```json
{"category":"CALL","subject":"통화 연결 문의","body":"통화가 연결되지 않았어요.","callId":123,"idempotencyKey":"ticket-20261011-1"}
```

transactionId는 현재 서비스의 TalkTimeTransaction ID이다. 결제 제공사의 주문 ID를 뜻하지 않는다.
jobType은 VOICE, FACE, VALUE_BALANCE, CLONE_PROFILE 중 하나이며 jobId와 함께 지정한다.
관련 거래·작업은 문의 회원 소유여야 하며 통화는 해당 회원이 참여했어야 한다. 다른 회원의 ID 연결은 거절된다.
관리자 상세 diagnostic은 기존 안전 통화 코드와 작업 상태를 조회한다. 실패 코드는 AI_JOB_FAILED, JOB_UNAVAILABLE로 정규화하고 worker 오류 메시지·원문 응답을 반환하지 않는다.

답변/메모/추가문의:

```json
{"body":"답변 내용","version":1,"idempotencyKey":"reply-1"}
```

배정: {"assigneeId":42,"version":0}. 상태 변경: {"status":"RESOLVED","version":2}.
이메일 접수: {"memberId":1,"externalMessageId":"메일 제공사 메시지 ID","ticket":{...접수 요청...}}.
회원 이메일·관련 정보를 확인한 운영자가 등록한다. 자동 수신 webhook이나 받은편지함 polling은 이번 범위에 포함하지 않는다.
외부 메시지 ID는 해시로 저장하며 동일 메일 재등록 시 기존 티켓을 반환한다.

## 이메일 발송

- `SUPPORT_MAIL_ENABLED=true`, `SUPPORT_MAIL_FROM`, `SMTP_USERNAME`, `SMTP_PASSWORD`를 서버 환경/비밀정보 저장소에서 주입한다.
- 기존 JavaMailSender를 사용하며 `SMTP_HOST`(기본 smtp.gmail.com), `SMTP_PORT`(기본 587)를 바꿀 수 있다. STARTTLS와 5초 연결·읽기·쓰기 제한을 적용한다.
- 발송 비활성은 기본값이다. 설정이 없으면 MAIL_NOT_CONFIGURED로 FAILED 처리하되 회원 문의함 답변은 남는다.
- 30초마다 최대 50건. PENDING → SENDING → SENT. 인증 실패는 FAILED / MAIL_AUTH_FAILED.
- SMTP 전송 예외나 10분 이상 미완료 발송은 UNKNOWN / DELIVERY_UNCERTAIN. 접수 여부를 확정할 수 없으므로 자동 재전송하지 않는다.
- FAILED는 새 재전송 키로 예약할 수 있다. UNKNOWN은 `acknowledgePossibleDuplicate:true`도 필요하다.
- 재전송 예: {"idempotencyKey":"retry-1","acknowledgePossibleDuplicate":true}.
- 동일 재전송 키는 다시 예약하지 않는다. SMTP 자체는 정확히 한 번 발송을 보장하지 않는다. 안정적인 Message-ID와 DB 발송 잠금은 중복 위험을 줄이지만 제공사의 중복 제거를 보장하지 않는다.
- 이메일 목적지는 발송 시점의 등록 회원 이메일이며 요청 본문으로 목적지를 지정할 수 없다. 탈퇴·삭제 회원에게는 CANCELLED / RECIPIENT_UNAVAILABLE로 발송을 막는다.
- 운영 로그·메트릭에는 회원 이메일, 본문, 내부 메모, 첨부 URL, SMTP 예외 원문을 기록하지 않는다.

## 첨부 및 운영 설정

- 기본 보관 30일, 티켓당 최대 5개, 파일당 5MiB. JPEG/PNG/PDF만 허용하며 실제 파일 signature·Content-Type·크기를 검사한다.
- `AWS_SUPPORT_BUCKET`을 비공개 전용 버킷으로 지정한다. 미설정이면 기존 S3 버킷의 support-private/ 경로를 사용하므로 해당 경로의 공개 접근을 반드시 막아야 한다.
- 업로드 요청: {"fileName":"capture.png","contentType":"image/png","size":12345}.
- PUT URL은 5분간 유효하고 응답 signedHeaders를 함께 보내야 한다. AES256 암호화를 적용한다. 업로드 후 confirm을 호출한다.
- 확정 시 S3 versionId/ETag를 저장하고 다운로드 요청에 사용한다. 미검사 파일·다른 티켓의 첨부·만료 첨부는 다운로드 불가능하다.
- GET URL도 최대 5분이며 파일의 만료 시각을 넘지 않는다. signedHeaders를 함께 사용한다. 강제 다운로드 및 application/octet-stream으로 제공한다.
- 매시간 만료 첨부 최대 100건 삭제. 실패는 삭제 완료로 표시하지 않고 다음 주기에 재시도한다. 대량 적재 환경에서는 S3 lifecycle을 함께 적용한다.
- `SUPPORT_ATTACHMENT_RETENTION_DAYS`는 1~365. 인프라 lifecycle과 같은 값으로 맞춘다.
- [infra/support-storage-and-alerts.yaml](../infra/support-storage-and-alerts.yaml)은 비공개 암호화 S3 버킷, 버전 보관·만료, TLS 정책, 이메일 실패/불확실 발송 및 첨부 삭제 실패의 CloudWatch Logs 지표·SNS 경보를 생성하는 CloudFormation 템플릿이다. 이 작업에서 실제 AWS 배포는 수행하지 않았다.
- 전용 버킷의 CORS는 프런트엔드 origin에 맞춰 PUT/GET 및 서명 헤더를 허용하도록 설정한다. 앱 IAM 역할에 해당 prefix의 s3:PutObject, s3:GetObject, s3:GetObjectVersion, s3:DeleteObject, s3:DeleteObjectVersion 권한이 필요하다.
- SMTP 비밀정보는 환경에서 주입하며 코드/DB에 저장하지 않는다. 기존 운영 CloudWatch 로그 그룹과 확인된 SNS topic ARN은 배포 템플릿 파라미터로 전달한다.
- 새 AI API와 외부 헬프데스크 연동은 추가하지 않는다. 기존 진단 데이터를 사용한다.

## DB 및 검증

V48__create_support_tickets.sql은 티켓, 메시지/발송 큐, 재전송 멱등키, 첨부 테이블을 추가한다.
H2 MySQL 모드에서 새 migration과 중복 키 제약을 검증한다. JPA 통합 테스트는 회원별 접근, 메모 분리, 버전 충돌, 발송 잠금/재전송, 첨부 확인·권한·만료·삭제 재시도를 검증한다.
SMTP 및 S3 테스트는 mock을 사용하며 실제 외부 발송이나 업로드를 실행하지 않는다.

검증 결과: 고객문의 테스트 15개 통과. 전체 316개 중 315개 통과, 기존 contextLoads 테스트 1개는 V9의 MySQL 다중 ADD COLUMN 문법을 H2가 지원하지 않아 실패했다. 운영 MySQL에서의 실제 migration 적용 및 CloudFormation 배포 검증은 별도 환경에서 필요하다.
