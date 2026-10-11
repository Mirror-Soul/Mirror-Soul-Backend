# 신고 및 안전 백엔드

## 기본 정책

- 계정 생명주기(UserStatus), 신고 처리 상태, 운영자 이용 제한, 회원 개인 차단(/blocks)을 분리한다.
- 신고 유형: HARASSMENT, THREATS, SEXUAL_CONTENT, FRAUD, SPAM, PRIVACY, OTHER.
- THREATS는 URGENT, SEXUAL_CONTENT/PRIVACY는 HIGH, 나머지는 NORMAL. 신고만으로 자동 제재하지 않는다.
- 신고 상태: RECEIVED -> REVIEWING -> DECIDED, 이의 제기 시 APPEALED -> DECIDED.
- 경고는 알림만 등록한다. RESTRICT는 기본 7일, 1~30일의 통화·메시지 발신·신규 AI 작업 제한이다.
- 안전 제재 중에도 로그인, 기존 내역 조회, 신고, 개인 차단, 이의 제기는 가능하다. 별도 관리자 계정 정지/탈퇴 상태는 기존 인증 제한을 유지한다.
- 이용 제한 기간은 시간 비교로 자동 만료된다. 수동 해제 및 이의 제기 수용은 해당 신고의 제재만 해제한다.
- 여러 신고의 제한이 겹치면 가장 늦은 활성 제재 만료 시각을 User에 반영한다. 한 제재 해제로 다른 제재를 해제하지 않는다.
- 같은 신고자/대상/유형/callId/messageId에 대한 24시간 내 중복 접수는 409. 같은 멱등 키와 같은 내용은 기존 접수 반환.
- 신고 접수 한도는 회원별 시간당 10건, 하루 50건(설정 가능), 초과 시 429.
- 이의 제기는 대상자만 WARN/RESTRICT 결정 후 14일 이내, 결정당 1건 접수. 재검토 중에도 제재는 유지한다.
- 이의 제기는 최초 결정자와 다른 관리자가 처리한다. 재검토 담당자는 다시 배정한다.

## 권한 및 증거

ADMIN_VIEWER는 신고 메타데이터/결정 이력 조회만 가능하다.
ADMIN은 본인에게 담당 배정 후 결정 및 증거 열람이 가능하다. SUPER_ADMIN은 다른 관리자에게 배정 및 담당자 외 처리 가능하다.
관리자는 본인이 신고자 또는 대상자인 건을 결정할 수 없다. 관리자 계정에 대한 결정은 SUPER_ADMIN만 허용한다.

증거는 비공개 DB의 신고 설명, 신고 시점의 메시지 원문 스냅샷, 이의 제기 설명, 통화 진단 코드이다.
임의 URL/파일 경로를 받지 않는다. 메시지 작성자가 신고 대상인지, 신고자가 작성 시점에 해당 채팅방 회원이었는지 확인한다.
통화는 신고자와 대상자가 실제 참가자인지 확인한다. 개인 차단 후에도 기존 통화/메시지 신고는 가능하다.
목록/상세 조회에는 원문 증거를 포함하지 않는다. 별도 증거 열람 API는 담당 관리자 또는 SUPER_ADMIN만 허용한다.
열람 사유는 REPORT_REVIEW/APPEAL_REVIEW/SECURITY_AUDIT 중 하나이다. 허용/거부 결과를 별도 트랜잭션 감사 테이블에 기록한다.
감사 조회는 SUPER_ADMIN 전용이다. 신고/이의 제기 입력 검증 실패에서 원문을 로그에 기록하지 않는다.

증거는 접수 후 기본 90일 보관하고 처리 결정/이의 제기 접수 시 90일 보관 기한을 연장한다.
기한 이후 열람을 즉시 거부하고, 시간당 최대 1,000건의 신고 증거 및 만료 진단 이벤트를 정리한다.
증거 삭제 후에도 접수 ID·처리 이력·사유 코드·감사 메타데이터는 유지한다.
결정 rationale은 대상자에게 전달하는 공개 안내문이다. 신고자 신원이나 원문 증거를 복사해서 넣지 않는다.

## 회원 API

- POST /reports
- GET /reports?page=0&size=20 : 본인 접수 목록
- GET /reports/{id} : 신고자만 접수 상태 조회
- POST /reports/{id}/appeals : 대상자 이의 제기 접수
- GET /safety/appeals : 본인 이의 제기 목록
- GET /safety/notices : 본인 결과 알림함
- POST /safety/notices/{id}/read : 본인 알림 읽음 표시
- GET /safety/sanctions : 본인 제재 이력
- GET /safety/status : 계정/통신/AI 작업 허용 여부 및 제한 만료 시각

페이지는 0부터, size는 1~100. 멱등 키는 영문/숫자/밑줄/하이픈 1~100자이며 소문자로 정규화한다.

```json
{"targetUserUuid":"대상 UUID","type":"HARASSMENT","description":"신고 설명","callId":123,"messageId":null,"idempotencyKey":"report-uuid"}
```

callId/messageId는 선택 사항. 신고자가 전달한 ID는 서버에서 관계 검증한다.

```json
{"decisionId":123,"explanation":"재검토 요청 내용","idempotencyKey":"appeal-uuid"}
```

## 관리자 API

- GET /admin/reports?priority=URGENT&state=RECEIVED&assigneeId=1&page=0&size=20
- GET /admin/reports/{id} : report.version, targetVersion, 최신 결정, 미처리 이의 제기 메타데이터
- PATCH /admin/reports/{id}/assignee : assigneeId, expectedVersion
- GET /admin/reports/{id}/decisions : 결정 이력
- POST /admin/reports/{id}/decisions : 처리 결정
- POST /admin/reports/{id}/evidence/access : {"reason":"REPORT_REVIEW"}
- GET /admin/reports/{id}/evidence/audits : 증거 접근 감사

```json
{"action":"RESTRICT","reasonCode":"VIOLATION_CONFIRMED","rationale":"이용 정책 위반으로 7일간 신규 통화와 메시지 발신이 제한됩니다.","expectedVersion":1,"expectedMemberVersion":0,"restrictionDays":7,"idempotencyKey":"decision-uuid"}
```

| action | reasonCode | 처리 조건 |
|---|---|---|
| REJECT | INSUFFICIENT_EVIDENCE / NO_VIOLATION | RECEIVED/REVIEWING |
| WARN | VIOLATION_CONFIRMED | RECEIVED/REVIEWING, 유효 증거 |
| RESTRICT | VIOLATION_CONFIRMED | RECEIVED/REVIEWING, 유효 증거, 이용 가능한 계정 |
| RELEASE | RESTRICTION_COMPLETED | DECIDED, 해당 신고의 활성 제재 |
| APPROVE_APPEAL | APPEAL_ACCEPTED | APPEALED, 다른 관리자 검토 |
| REJECT_APPEAL | APPEAL_REJECTED | APPEALED, 다른 관리자 검토 |

expectedVersion은 신고 버전. 제재 생성/활성 제재 해제에는 expectedMemberVersion도 필요하다.
대상 User 행을 먼저 잠그고 신고 행을 잠근다. 같은 키·같은 담당자·같은 요청은 최초 결정 결과를 반환한다.
변경된 키 내용, 버전 충돌, 이미 처리된 건은 409. 신고/제재/이의 제기 상태 및 결과 알림 저장은 하나의 트랜잭션이다.

통지는 DB 알림함 저장이며 notificationResult=INBOX_STORED로 반환한다. 읽음 상태는 별도 제공한다.
신고자에게 처리 결과를, 대상자에게 경고/제재/해제/이의 제기 결과를 전달한다. 대상자에게 신고자 신원이나 증거를 노출하지 않는다.
푸시/이메일 발송을 전달 성공으로 간주하지 않는다. 현재 구현에는 외부 발송이 없다.

## AI 및 통화 연결 계약

- GET /internal/ai/safety/users/{uuid}/permissions
- POST /internal/ai/safety/calls/{id}/diagnostics

내부 API는 기존 X-Internal-Api-Key 인증으로 보호한다.

```json
{"code":"CONNECTION_FAILED","eventKey":"diagnostic-uuid"}
```

진단 코드: CONNECTED, CONNECTION_FAILED, DISCONNECTED, AI_ERROR, SAFETY_STOPPED.
callId별 이벤트 키는 멱등 처리하고 같은 키의 다른 코드는 거부한다. callId별 최대 1,000건, 임의 원문 로그/녹음 내용은 받지 않는다.
신고의 callId로 진단 이벤트를 증거 열람과 연결한다. 시그널링 전달 실패도 진단 이벤트로 저장한다.

통화 생성·연결 완료·AI 컨텍스트 조회, 채팅 발신, 만남 요청/수락, 음성/얼굴 학습 생성,
가치관 분석 요청, 추천 임베딩 및 클론 프로필 생성에 이용 허용 조건을 적용한다.
AI 서버는 큐에서 받은 기존 작업도 실제 실행 직전에 permissions.aiWorkAllowed를 확인해야 한다.
제재된 회원은 신규 추천 후보에서 제외한다. 통화 종료는 제재 중에도 가능하다.

시그널링 WebSocket /ws/signaling의 업그레이드에도 인증이 필요하다.
회원 연결은 Authorization Bearer 인증 principal, AI 서버 연결은 X-Internal-Api-Key를 사용한다.
회원은 본인 통화의 caller signal ID만 사용 가능하고 ai-server/AI signal ID는 인증된 AI 세션만 사용 가능하다.
메시지마다 참가자 제재 상태와 roomId/callId 및 회원 세션 버전을 재검사한다. 제재 상태에서도 CALL_END/LEAVE는 허용한다.
기존 무인증 WebSocket 클라이언트는 인증 정보를 전달하도록 변경해야 한다.

## 설정 및 운영 연동

- safety.reports.hourly-limit=10
- safety.reports.daily-limit=50
- safety.evidence.retention-days=90
- safety.evidence.cleanup-delay-ms=3600000
- safety.restriction.default-days=7
- safety.appeal.days=14

DB 마이그레이션: V47__add_report_safety.sql.
긴급 신고, 중복/대량 접수, 결정 실패, 증거 정리 실패는 내용 원문 없이 구조화한 애플리케이션 로그로 기록한다.
로그를 실제 알림 채널에 연결하고 백업 보관 정책 및 DB 접근 권한을 구성하는 일은 인프라 운영 작업이다.
사용자 첨부 이미지·녹음 파일 업로드 및 외부 저장소의 증거 삭제는 현재 증거 계약에 포함하지 않는다.
