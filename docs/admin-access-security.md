# 관리자 접근·보안

## 구현 범위

Spring의 관리자 인증/업무 권한을 구현한다. Vercel의 로그인 허용과 Spring 관리자 권한은 서로 독립적이다.
기존 ADMIN, ADMIN_VIEWER, SUPER_ADMIN 역할을 유지하며 업무별 권한을 추가한다. 모든 관리자 요청은 DB의 현재 ACTIVE 상태와 권한을 확인한다.
V49__add_admin_access_security.sql은 직원 프로필, 업무 권한, 재인증 해시, 변경 멱등키, 감사 이벤트를 추가한다.
Vercel/Redis 설정 변경이나 AWS 배포는 실행하지 않았다. 관리자 원본 경로의 Cloudflare/Tunnel/AWS 보호는 후속 작업으로 둔다.

## 권한

| 권한 | 업무 |
|---|---|
| job_review | 직업 서류 조회·승인·거절 |
| member_read | 회원 목록·상세·조치 이력 조회 |
| member_manage | 일반 회원 상태 조치 |
| moderation_read | 신고 메타데이터·처리 이력 조회 |
| moderation | 신고 담당자·조치·제한된 증거 접근 |
| support_read | 고객문의 목록·상세 |
| support_reply | 고객문의 답변·메모·담당자·상태·재전송·제한된 첨부 접근 |
| billing_adjust | 결제 조정 권한 식별자. 현재 별도 결제 조정 API는 없음 |
| audit_read | 관리자 감사 이벤트·신고 증거 감사 조회 |
| staff_read | 직원 목록 조회 |
| staff_manage | 직원 초대·권한 변경·회수·고권한 재인증. SUPER_ADMIN 전용 |

기존 ADMIN은 job_review/member_read/member_manage/moderation_read/moderation/support_read/support_reply를 기본으로 갖는다.
ADMIN_VIEWER는 member_read/moderation_read/support_read만 갖는다. SUPER_ADMIN은 모든 권한을 갖는다.
직원 프로필이 없으면 위 기본 권한으로 호환한다. 프로필 생성 후에는 명시적으로 저장한 권한만 사용한다.
기존 VIEWER에게는 조회 권한만 추가로 부여할 수 있다. 직원 계정의 ACTIVE 상태는 회원 상태와 직원 프로필 상태가 모두 활성인 경우에만 인정한다.
기존 담당자·SUPER_ADMIN·신고 증거·조치별 제한도 함께 적용된다. 예를 들어 support_reply가 있어도 다른 담당자의 첨부를 읽을 수 없다.
직원 계정 변경은 회원 상태 조치 API로 우회할 수 없다. 자기 자신 및 다른 SUPER_ADMIN의 직원 회수/권한 변경을 금지한다.

## API

기존 JWT 인증을 사용한다. 모두 기존 ApiResponse 포맷이다.

| API | 응답/기능 |
|---|---|
| GET /admin/me | staffId, displayName, accountState, permissions, version |
| GET /admin/staff?page=0&size=20 | 기존 직원·초대·회수 직원 목록 |
| POST /admin/reauth | 현재 비밀번호로 5분짜리 일회용 재인증 토큰 |
| POST /admin/staff/invitations | 기존 활성 회원에 대한 24시간 직원 초대 |
| POST /staff-invitations/activate | 초대받은 회원 본인 JWT와 초대 토큰으로 수락 |
| PATCH /admin/staff/{id}/permissions | 업무 권한 변경 |
| POST /admin/staff/{id}/revoke | 직원 권한과 기존 세션 회수 |
| GET /admin/audit-events | 직원·결과·from/to 조건의 감사 목록 |

목록 페이지는 0부터 시작하고 size는 1~100이다.
재인증 본문: `{"password":"현재 비밀번호"}`. 재인증은 SUPER_ADMIN만 가능하고 15분에 5번까지 시도할 수 있다. 실패 시도도 영속적으로 집계한다.
직원 초대·권한 변경·회수 요청은 `X-Admin-Reauth` 헤더에 재인증 토큰을 보낸다. 토큰은 직원 ID·sessionVersion과 묶여 있으며 DB에는 해시만 저장한다. 성공한 신규 조치에서 한 번 소비한다.

초대:

```json
{"memberId":123,"displayName":"상담 담당자","permissions":["support_read","support_reply"],"idempotencyKey":"staff-invite-1"}
```

초대 대상은 기존 ACTIVE 일반 회원이다. 초대 생성만으로 ADMIN 권한이 생기지 않는다.
반환한 activationToken을 대상에게 안전하게 전달한다. 자동 초대 이메일 발송은 추가하지 않았다.
수락 본문은 `{"token":"activationToken"}`이다. 수락은 본인만 가능하며 성공하면 기존 토큰을 폐기하므로 다시 로그인해야 한다.
초대 토큰은 재시도 응답을 위해 AES-256-GCM으로 암호화 저장하고 활성화용 해시는 별도로 저장한다. 같은 초대 키를 재시도하면 유효한 기존 초대 토큰을 다시 반환한다.
암호화 키가 없으면 초대 생성은 실패하며 권한 변경은 커밋되지 않는다. 활성화/회수 시 초대 암호문을 삭제한다.
회수된 기존 직원은 새 초대 키로 다시 초대할 수 있다. 만료 초대는 회수 후 새로 초대한다.

권한 변경:

```json
{"permissions":["member_read"],"version":0,"idempotencyKey":"staff-permissions-1"}
```

회수: `{"version":1,"idempotencyKey":"staff-revoke-1"}`.
멱등키는 영문·숫자·밑줄·하이픈 1~100자이며 대소문자를 구분하지 않는다. 같은 키에 다른 조치를 보내거나 오래된 version을 사용하면 STAFF_4090.
같은 완료 조치의 재시도는 다시 실행하지 않는다. 같은 초에 같은 권한을 설정해도 직원 version은 증가한다.

## 토큰·다중 접속 정책

- 기존 JWT 접근 토큰 기본 수명은 1시간이다. 서버는 매 요청 사용자 상태·sessionVersion을 재조회한다.
- 여러 기기의 접근 토큰은 허용한다. 기존 구조상 회원별 최신 refresh token 한 개를 저장하므로 새 로그인/갱신 이후 이전 refresh token은 사용할 수 없다.
- 권한 변경·회수·초대 활성화는 sessionVersion을 증가시키고 저장 refresh token을 삭제한다. 모든 기기의 기존 접근/갱신 토큰은 즉시 무효다.
- 권한 회수는 역할을 USER로 낮추며 REVOKED 직원 프로필을 남긴다. 일반 서비스 로그인과 관리자 권한은 구분된다.
- 재인증은 같은 계정·현재 sessionVersion에 묶이며 브라우저 기기별 세션 ID를 별도로 저장하지 않는다.
- SUPER_ADMIN은 별도 bootstrap/운영 복구 경로로 관리한다. 공개 API에서 SUPER_ADMIN 생성이나 상호 회수를 제공하지 않는다.

## 감사 일관성

감사 이벤트는 staffId, action, target, occurredAt, result, reasonCode, correlationId만 저장한다. 회원·신고 조치의 reasonCode는 검증된 기존 사유 enum을 사용하고 직원 관리에는 STAFF_INVITED/ACCESS_POLICY_CHANGED/STAFF_ACCESS_REVOKED 등의 서버 정의 코드만 사용한다.
관리자 서비스의 업무 변경과 성공 감사 기록은 동일한 DB 트랜잭션에 들어간다. 감사 저장이 실패하면 업무 변경도 롤백된다.
조회·거절·실패 기록은 별도 트랜잭션에 저장하며, 보안 필터에서 차단된 요청도 기록한다.
감사 목록 조회 자체도 기록된다. 상관 ID는 유효한 UUID만 수용하고 아니면 새 UUID를 발급해 X-Correlation-Id 응답 헤더로 돌려준다.
DB 감사가 커밋된 후에만 `Admin audit committed` 운영 로그를 남긴다. 비밀번호, 내부 키, JWT, 재인증/초대 토큰, 검색어, 문의/메모/대화 본문, 원본 얼굴·음성은 감사 필드와 이 로그에 넣지 않는다.
기본 보관은 365일이며 `ADMIN_AUDIT_RETENTION_DAYS`는 90~3650일이다. 매일 04시에 이전 기록을 삭제하고 하루 이상 만료된 재인증 해시도 정리한다.
[infra/admin-audit-alerts.yaml](../infra/admin-audit-alerts.yaml)은 실제 앱 로그 그룹의 감사 저장/정리 실패를 CloudWatch 지표와 SNS 경보로 연결한다. 실제 AWS 배포는 별도로 해야 한다.

## Origin과 내부 키

- `CORS_ALLOWED_ORIGIN_PATTERNS`: 실제 앱 Origin 목록을 쉼표로 지정한다. 기본은 localhost:3000/5173이며 공개 wildcard 기본값을 제거했다. JWT 헤더 방식이므로 credential cookie 허용은 껐다.
- `ADMIN_ALLOWED_ORIGINS`: 관리자 Origin의 정확한 목록이다. Origin을 가진 관리자 요청은 이 목록과 일치해야 한다. 값에 경로나 trailing slash를 붙이지 않는다.
- Origin이 없는 서버 요청도 JWT·ACTIVE·업무 권한 검사를 통과해야 한다. Vercel 통과 또는 Origin 값만으로 ADMIN이 되지 않는다.
- 내부 AI 키는 call_context/talk_log/safety/member_deletion/signaling 범위로 제한한다. `AI_INTERNAL_SCOPES`로 필요한 범위만 지정한다. 알려지지 않은 내부 AI 경로는 거절한다.
- `X-Internal-Api-Key` 현재 키: `AI_INTERNAL_API_KEY`. 이전 키: `AI_INTERNAL_PREVIOUS_API_KEY`, 명시적 만료: `AI_INTERNAL_PREVIOUS_KEY_EXPIRES_AT`(UTC ISO-8601).
- 기존 통화 signaling handshake도 같은 키 교체와 signaling 권한을 확인한다.
- 가치관 콜백은 별도 `VALUE_BALANCE_CALLBACK_SECRET`, 이전 키/만료는 `VALUE_BALANCE_PREVIOUS_CALLBACK_SECRET` / `VALUE_BALANCE_PREVIOUS_SECRET_EXPIRES_AT`.
- 클론 콜백은 별도 `CLONE_TRAINING_CALLBACK_SECRET`, 이전 키/만료는 `CLONE_TRAINING_PREVIOUS_CALLBACK_SECRET` / `CLONE_TRAINING_PREVIOUS_SECRET_EXPIRES_AT`.
- 키 비교는 일정 시간 비교를 사용하고 현재 키가 없으면 이전 키만으로 인증하지 않는다. 다른 업무의 콜백 키를 공유하지 않는다.
- 새 JWT는 `JWT_SECRET`으로 서명한다. 공개 기본 키를 제거했으므로 서버에 최소 32바이트의 충분히 임의적인 비밀 키가 필요하다.
- JWT 이전 키 유예는 `JWT_PREVIOUS_SECRET`, `JWT_PREVIOUS_SECRET_EXPIRES_AT`로 제한한다. 현재 키의 서명 검증 실패에만 이전 키 검증을 시도하며 만료/토큰 유형/sessionVersion 검사는 유지한다.
- `ADMIN_INVITE_ENCRYPTION_KEY`는 서버에 저장하는 Base64 인코딩 32바이트 AES 키이다. DB/프런트엔드/로그에 공개하지 않는다.
- 내부 AI 및 콜백의 입력 오류 응답은 정규화하며 검증 예외에 포함된 원본 대화/음성/재인증 값을 로그로 출력하지 않는다. 공통 예외 처리도 원문 예외·거절된 값·파서 메시지를 로그로 출력하지 않고, 예상하지 못한 오류의 원문 메시지를 API에 반환하지 않는다.

## 운영 설정 및 검증 절차

1. DB V49를 적용하고 기존 SUPER_ADMIN으로 /admin/me의 권한을 확인한다. 일반 회원·VIEWER의 쓰기/자료 접근 거절을 확인한다.
2. 비밀정보 저장소/서버 환경에 JWT, AI, 각 콜백, 초대 암호화 키를 각각 주입한다. 키를 repo·Vercel 브라우저 공개 환경변수·지원 문의에 남기지 않는다.
3. Vercel 측 운영자 개인 계정, 복구 경로, MFA, Authentication 및 All Deployments 보호는 운영 환경에서 설정하고 별도로 검증한다. Spring 권한을 같은 설정으로 대체하지 않는다.
4. Redis를 사용하는 프런트엔드/세션 계층은 TLS, 별도 쓰기 인증, 최소 네트워크 접근, 세션 암호화 키의 서버 보관을 설정한다. 이 백엔드는 관리자 세션을 Redis에 새로 저장하지 않고 DB+JWT를 사용한다.
5. 앱 운영 로그를 CloudWatch 등으로 수집하고 감사 조회 권한과 로그 저장소 읽기 권한을 별도로 제한한다. 운영 로그 보관 기간도 DB 감사 정책에 맞춰 설정한다. 감사 실패 경보 템플릿에 실제 로그 그룹·확인된 SNS topic을 지정한다.
6. 신규 키를 서버 현재 키로 배포하고 기존 키는 명시적 만료가 있는 이전 키로만 유지한다. 콜백 소비자/AI를 순차 전환한다. 만료 후 이전 키를 삭제하고 현재 키 성공·이전 키 실패·업무 범위 밖 거절을 확인한다.
7. 초대 암호화 키 교체 시 유효한 초대의 재시도 응답이 필요한 동안 기존 암호화 키를 유지한다. 24시간 초대 만료 또는 활성화/회수 완료 후 교체한다. 초대 활성화 해시는 암호화 키와 별개다.
8. 권한 회수 검증: 실제 직원 JWT로 허용 업무를 실행하고 권한 변경/회수 후 같은 접근 토큰·갱신 토큰·재인증 토큰이 차단되는지 확인한다. 다른 기기의 토큰도 확인한다. 자기 자신/다른 SUPER_ADMIN 회수는 거절된다.
9. 감사 장애 검증: 테스트 환경에서 감사 INSERT를 실패시켜 업무 변경도 롤백되는지 확인한다. 거절/실패 기록과 경보를 확인한다. 운영 DB에서 장애를 의도적으로 발생시키지 않는다.
10. 롤백은 V49 테이블·직원 회수·sessionVersion 상태를 보존한다. 새 ACL을 사용한 이후 기존 권한 검사만 있던 버전으로 되돌리면 권한 회수가 우회될 수 있으므로 같은 ACL을 적용한 호환 빌드로 롤백한다. V49를 삭제하거나 sessionVersion을 낮추지 않는다. 이전 키 복구도 유예 만료 및 침해 여부를 확인하고 수행한다.

실제 Vercel/Redis/클라우드 설정과 알림 수신, 운영 MySQL 마이그레이션은 환경 접근 권한이 있는 운영자가 적용해야 한다.

## 검증 결과

접근보안 신규 테스트 14개가 모두 통과했다. 실제 Spring 보안 필터·MVC 인터셉터·감사 연결, 권한 회수 후 기존 JWT 차단, 감사 저장 실패 시 업무 롤백, 재인증 제한, 초대 암호화/멱등성, 이전 키 만료·서명 알고리즘별 교체, 예외 로그의 원문 비밀정보 배제를 검증했다.
전체 회귀 테스트는 330개 중 329개 통과했다. 기존 contextLoads 1개는 V9__add_call_room_and_media_type.sql의 MySQL 다중 ADD COLUMN 문법을 H2가 지원하지 않아 실패한다.
새 V49는 H2 MySQL 모드에서 별도로 검증했다. 실제 운영 MySQL 적용과 CloudFormation 배포는 수행하지 않았다.
