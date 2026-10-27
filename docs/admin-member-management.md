# 관리자 회원 관리 정책 및 API

## 상태 및 권한

기존 ONBOARD_A/B/C/D, ACTIVE 가입 흐름과 INACTIVE 탈퇴 대기 상태를 유지한다.
SUSPENDED는 정지, WITHDRAWAL_CONFIRMED는 복구 불가 및 삭제 대기,
DELETED는 기존 백엔드 개인정보 익명화 완료 상태다.
조회 응답은 onboardingStage와 accountStatus(NORMAL/SUSPENDED/WITHDRAWAL_PENDING/WITHDRAWAL_CONFIRMED)를 분리한다.
기존 탈퇴 대기 회원의 이전 가입 단계는 저장되어 있지 않으므로 null일 수 있으며 복구는 기존처럼 ACTIVE로 처리한다.
신규 탈퇴는 이전 상태를 보존하고, 정지 회원의 복구는 정지 상태로 돌아간다.

기존 정책에 따라 탈퇴 요청 후 30일까지 복구한다(정확히 30일 시점부터 불가).
정상 회원은 기존처럼 기간 내 로그인으로 복구되며 정지 회원은 관리자 복구가 필요하다.
ADMIN_VIEWER는 회원 GET API만 허용, ADMIN은 조회 및 일반 계정 조치를 허용한다.
SUPER_ADMIN은 즉시 탈퇴 확정 및 관리자 계정에 대한 조치를 허용한다.
관리자의 자기 계정 조치는 금지한다. 기존 ADMIN 권한을 자동 승격하지 않는다.

## API

- GET /admin/members?search=&accountStatus=&onboardingStage=&page=0&size=20
  - search: 숫자 ID 또는 UUID 일치, 이메일 부분 검색. SQL 검색 와일드카드는 일반 문자로 처리.
  - onboardingStage: ONBOARD_A/B/C/D/ACTIVE.
  - page는 0부터 시작, size는 1~100, ID 내림차순.
- GET /admin/members/{id}
  - maskedEmail, job, 가입 단계, 계정 상태, version, 가입/변경/탈퇴/익명화 시각 반환.
  - 현재 User에는 전화번호가 없으므로 이메일을 마스킹한다. 비밀번호/인증 토큰은 반환하지 않는다.
- GET /admin/members/{id}/actions?page=0&size=20
- POST /admin/members/{id}/actions
- GET /admin/members/{id}/deletion
  - 삭제 작업이 아직 없으면 result는 null.

조치 요청 예시:

```json
{"action":"SUSPEND","reasonCode":"SECURITY_RISK","expectedVersion":0,"idempotencyKey":"request-uuid"}
```

| action | 허용 상태 | 사유 코드 | 결과 |
|---|---|---|---|
| SUSPEND | 가입 중 또는 ACTIVE | POLICY_VIOLATION, SECURITY_RISK, ADMIN_CORRECTION | SUSPENDED |
| UNSUSPEND | SUSPENDED | REVIEW_COMPLETED, ADMIN_CORRECTION | 정지 이전 상태 |
| WITHDRAW | 가입 중, ACTIVE, SUSPENDED | USER_REQUEST, ADMIN_CORRECTION | INACTIVE |
| RECOVER | 복구 기간 내 INACTIVE | USER_REQUEST, ADMIN_CORRECTION | 탈퇴 이전 상태 |
| RETRY_DELETION | 확정/익명화 계정, 실패 단계 존재 | REVIEW_COMPLETED, ADMIN_CORRECTION | 실패한 단계만 추가 3회 시도 허용 |
| CONFIRM_WITHDRAWAL | INACTIVE | USER_REQUEST, ADMIN_CORRECTION | WITHDRAWAL_CONFIRMED + 삭제 작업 생성 |

버전 충돌, 불가능한 상태 전환, 동일 키의 다른 요청은 409.
멱등 키는 회원 단위이며 영문/숫자/하이픈/밑줄 1~100자, 대소문자를 구분한다.
동일 담당자·조치·사유·expectedVersion·키 재요청은 최초 결과를 반환한다.
성공 조치의 전후 상태·담당자·시각을 영구 이력 테이블에 저장한다.
회원 조회 및 성공 조치는 애플리케이션 로그에도 기록하며 검색어와 이메일 원문은 로그에 넣지 않는다.
정지/탈퇴 시 refresh token을 삭제하고 sessionVersion을 증가시켜 이전 access token도 무효화한다.

## 탈퇴 확정 및 삭제 연동

기존 일일 스케줄러가 30일 경과 계정을 확정하고 DB에 삭제 작업을 생성한다.
계정 조치와 작업 생성은 같은 트랜잭션이다. 복구는 확정 이후 불가능하다.
매 60초 backend 정리 워커가 최대 100개의 미완료 작업을 처리한다.
기존 백엔드 정리 범위인 회원 개인정보 익명화, 푸시 기기 삭제, 직업 인증 요청/증빙 삭제를 수행한다.
실패는 별도 트랜잭션으로 기록하여 롤백 후에도 남긴다. 최대 3회 시도하고 실패 로그를 발생시킨다.
RETRY_DELETION 조치로 실패한 단계만 추가 3회 재시도할 수 있다. 시도 번호는 계속 증가하여 과거 AI 결과가 새 시도에 적용되지 않는다.
backendState와 aiState를 따로 표시하고 두 단계 완료일 때만 complete=true로 반환한다.

AI 서버는 DB에 기록된 탈퇴 확정 작업을 다음 내부 API로 가져간다(내구성 있는 polling 방식).
모든 내부 API는 기존 X-Internal-Api-Key 인증이 필요하다.

1. GET /internal/ai/member-deletions?page=0&size=20
2. POST /internal/ai/member-deletions/{jobId}/claim
   - 작업 ID, 회원 ID/UUID, aiAttempts, 1시간 lease 반환.
3. AI 측 학습 원본·산출물·작업 정리.
4. POST /internal/ai/member-deletions/{jobId}/result

```json
{"memberId":123,"attempt":1,"state":"COMPLETED"}
```

state는 COMPLETED 또는 FAILED. FAILED 후 재할당 가능, 최대 3회 시도.
중복 결과는 멱등 처리하며 다른 회원·이전 시도 결과 또는 완료를 실패로 변경하는 요청은 거부한다.
할당이 만료된 작업은 실패로 전환하고 다시 가져갈 수 있다(최대 횟수 이후에는 중단).

## 남은 운영 및 외부 연동

AI 서버의 polling/실제 정리 구현은 이 저장소 밖의 작업이다.
전체 데이터 삭제가 필요하면 학습 자료 외 채팅/녹음/추천 벡터/파일 등 보존·삭제 범위를 별도로 확정하고 확장해야 한다.
현재 backendState 완료는 위에 명시한 기존 백엔드 정리 범위의 완료를 뜻한다.
관리자 수동 재시도 UI, 회원 알림, 로그 수집·열람 권한·보관 기간·백업 정책·외부 실패 경보 연결은 후속 작업이다.
일일 확정 작업 cron: account.cleanup.cron, zone: account.cleanup.zone.
백엔드 재시도 주기: account.cleanup.retry-delay-ms(기본 60000).

DB 변경: V45 SQL(버전·세션·이력·삭제 작업), V46 Java(권한 CHECK 확장 및 MySQL 멱등 키 binary collation).
V46은 MySQL과 H2의 CHECK 삭제 문법 차이를 처리한다.
