# 결제와 대화 시간 운영

스토어 서버가 검증한 소모성 상품만 시간을 지급한다. 지급, 사용, 환불 회수, 환불 취소 반환, 관리자 보정은 talk_time_transactions에 기록한다. 잔액의 기준은 원장 합계이며 users.remaining_talk_time은 같은 트랜잭션에서 갱신하는 캐시다.

## 환경설정

상품 ID와 지급 시간은 배포 환경변수로 지정한다. 실제 판매 상품을 등록하기 전 기본값은 0초여서 지급할 수 없다.

| 변수 | 용도 |
| --- | --- |
| APPLE_TALK_TIME_PRODUCT_ID / APPLE_TALK_TIME_PRODUCT_SECONDS | Apple 소모성 상품 ID / 1개당 지급 초 |
| GOOGLE_TALK_TIME_PRODUCT_ID / GOOGLE_TALK_TIME_PRODUCT_SECONDS | Google 일회성 소모성 상품 ID / 1개당 지급 초 |
| BILLING_ENCRYPTION_KEY | 구매 참조값 암호화용 32바이트 키의 Base64 값 |
| APPLE_BUNDLE_ID / APPLE_APP_ID | Bundle ID / 숫자 App Apple ID (운영 환경 필수) |
| APPLE_ISSUER_ID / APPLE_KEY_ID | App Store Server API 키 식별자 |
| APPLE_PRIVATE_KEY_PATH | 서버에 마운트한 .p8 개인 키 파일 경로 |
| APPLE_ROOT_CERTIFICATE_PATHS | 신뢰할 Apple 루트 인증서 파일 경로 목록, 쉼표 구분 |
| APPLE_STORE_ENVIRONMENT | PRODUCTION 또는 SANDBOX |
| GOOGLE_PLAY_PACKAGE_NAME | Android 패키지명 |
| GOOGLE_PLAY_CREDENTIALS_PATH | Android Publisher API 권한이 있는 서비스 계정 JSON 파일 경로. 비어 있으면 ADC 사용 |
| GOOGLE_PLAY_PUSH_AUDIENCE | Pub/Sub push OIDC 토큰의 audience |
| GOOGLE_PLAY_PUSH_SERVICE_ACCOUNT | 허용하는 push 서비스 계정 이메일 |
| GOOGLE_PLAY_ALLOW_TEST_PURCHASES | 테스트 구매 허용 여부, 기본 false |
| BILLING_RETRY_INTERVAL_MS | 결제 큐 조회 간격, 기본 30000 |
| BILLING_CALL_EXPIRY_INTERVAL_MS | 잔액 소진 통화 정산 간격, 기본 5000 |

예: APPLE_TALK_TIME_PRODUCT_SECONDS=1800은 검증된 상품 1개에 30분을 지급한다. 클라이언트가 지급 초를 보내지 않는다.

여러 상품은 배포용 외부 YAML을 SPRING_CONFIG_ADDITIONAL_LOCATION으로 읽거나 SPRING_APPLICATION_JSON으로 같은 맵을 제공한다. 다음 ID는 설명용이며 실제 스토어 ID로 바꿔야 한다.

    billing:
      apple-products:
        "[com.example.talk.30min]": 1800
        "[com.example.talk.2hours]": 7200
      google-products:
        "[talk_30min]": 1800
        "[talk_2hours]": 7200

맵 키의 대괄호는 Spring 설정 바인딩에서 상품 ID를 그대로 보존한다. 기존 결제는 최초 검증 때 확정한 상품/수량/지급 초를 유지한다. 변경한 가격이나 시간은 신규 결제에만 적용된다.

개인 키와 서비스 계정 JSON은 Secret Manager 등에서 읽어 제한된 권한의 파일로 마운트한다. 암호화 키는 환경변수에 주입하되 로그/저장소에 넣지 않는다. 구매 참조값은 AES-256-GCM으로 DB에 보관하고 API/작업 로그에 반환하지 않는다. 복구할 때 같은 암호화 키가 필요하므로 무계획으로 교체하면 안 된다. 키 버전별 복호화와 재암호화를 준비한 뒤 교체한다.

## 모바일 결제 계약

구매를 시작할 때 Apple appAccountToken과 Google obfuscatedAccountId에 로그인 회원의 UUID를 설정한다. 서버는 스토어 응답에 들어 있는 UUID와 인증된 회원을 비교한다. 기존에 계정 연결 없이 구매한 거래는 자동 지급하지 않는다.

회원 JWT로 POST /payments/verify를 호출한다.

    {"store":"APPLE","reference":"스토어 transactionId"}
    {"store":"GOOGLE","reference":"스토어 purchaseToken"}

성공 응답은 기존 ApiResponse 형식 안에 결제 ID, 거래 ID, 검증/지급/환불/소비 상태, 지급/회수 초, 원장 ID 목록을 포함한다. 같은 참조값을 재전송해도 지급은 한 번이다. 등록되지 않은 상품, 계정 불일치, 구매 대기는 지급하지 않는다. Google 상품의 consume은 지급과 함께 생성된 서버 큐 작업에서 처리하므로 앱에서 먼저 consume하지 않는다. Apple 거래 finish는 서버 지급 성공을 확인한 후 수행한다.

GET /payments/talk-time은 balanceSeconds(서명 있는 원장 잔액), availableSeconds(0 이상 사용 가능 초)를 반환한다. 환불 전에 이미 사용한 시간은 음수 잔액으로 남고 다음 지급에서 상계된다.

POST /my-page/buy-time과 POST /home/talk-time/refill은 직접 충전을 허용하지 않고 400을 반환한다. 기존 앱은 구매 검증 API로 전환해야 한다. 기존 잔액 조회와 홈 조회도 원장 합계를 사용한다.

실패 계약: 잘못된 입력/거래/미등록 상품은 400, 계정 또는 멱등성 충돌은 409, 설정 누락/스토어 일시 장애는 503이다. 실패 응답을 성공으로 간주하지 말고 동일 구매 참조값으로 재시도한다.

## 스토어 서버 알림

- Apple: POST /payments/notifications/apple, 본문 signedPayload. 공식 라이브러리로 인증서 체인, 서명, 앱, 환경을 검증한다.
- Google: POST /payments/notifications/google, Pub/Sub push message.data(Base64)와 message.messageId. Authorization의 OIDC 서명/audience/서비스 계정 이메일을 검증하고 메시지 패키지명을 확인한다.

인증된 알림을 DB 큐에 저장한 후 204를 반환한다. 이 응답은 접수 완료이며 지급/환불 처리 완료를 뜻하지 않는다. 같은 알림 ID는 동일 내용만 허용한다. DB 저장이 실패하면 성공을 반환하지 않으므로 스토어/Pub/Sub 재전송 대상이 된다.

환불 알림이 앱 구매 검증보다 먼저 도착해도 해당 거래의 회수 상태를 먼저 보관해 추후 지급을 막는다. 전체 환불/취소는 지급한 시간을 회수한다. Google 수량별 부분 환불은 현재 refundableQuantity를 다시 조회하고, Apple 부분 환불은 검증된 revocationPercentage에 따라 회수 초를 올림한다. 기존 환불 거래를 다시 검증해도 재지급하지 않는다. Apple REFUND_REVERSED 알림은 회수했던 초를 반환한다. 오래된 알림은 최신 환불 시각보다 앞서면 무시한다.

이 서버는 실제 금전 환불을 시작하지 않는다. 스토어에서 확정한 취소/환불/취소 철회 결과를 대화 시간에 반영한다.

## 관리자 API와 감사

| API | 기능 / 권한 |
| --- | --- |
| GET /admin/payments | 결제 상태, 거래 ID, 원장 연결 조회 / billing_read |
| GET /admin/members/{id}/talk-time-ledger | 지급/사용/회수/반환/보정, 초, 거래 ID/callId 조회 / billing_read |
| POST /admin/talk-time-adjustments | 시간 보정 / billing_adjust |
| GET /admin/payments/{id}/events | 큐 처리 상태/실패 코드 조회 / billing_read |
| POST /admin/payments/events/{id}/retry | 실패 작업 재시도 예약 / billing_adjust |

결제 조회 필터: store, memberId, transactionId, verificationStatus, grantStatus, refundStatus. 페이지는 0부터, size는 1~100이다. 관리자/VIEWER 기본 권한은 읽기만 포함하고, 보정은 SUPER_ADMIN 또는 명시적으로 billing_adjust 권한을 받은 직원만 가능하다.

보정 본문:

    {"memberId":123,"deltaSeconds":600,"reason":"고객지원 보상","idempotencyKey":"support-ticket-123"}

음수 보정도 가능하며 0은 거부한다. 같은 관리자와 멱등성 키에 같은 회원/초/사유를 보내면 최초 결과를 반환하고, 내용을 바꾸면 409다. 보정 원장, 결제 감사, 관리자 감사는 함께 커밋한다.

재시도 본문은 {"idempotencyKey":"retry-ticket-123"}이다. PROCESSING/COMPLETED 작업은 리셋하지 않는다. 재처리 권한과 감사 기록을 적용하고, 원장의 고유 작업 키로 중복 지급/회수를 막는다.

## AI 통화 정산 계약

POST /internal/ai/calls/{callId}/billing-events에 X-Internal-Api-Key를 사용한다. 내부 키에는 call_billing scope가 필요하다. 기존 내부 키 설정과 동일하게 경로별 권한을 적용한다.

    {"eventId":"call-42-start","kind":"STARTED","occurredAt":"2026-10-11T10:00:00+09:00"}
    {"eventId":"call-42-end","kind":"ENDED","occurredAt":"2026-10-11T10:01:00+09:00","endReason":"AI_HANGUP"}

실제 미디어 연결 시 STARTED, 종료 시 ENDED를 전송한다. eventId는 재시도 동안 유지한다. 같은 이벤트 ID와 다른 내용은 409다. 시작보다 먼저 도착한 정상 종료는 409이므로 시작 이벤트 처리 후 다시 전송한다. 연결 전 취소는 endReason=CANCELLED이며 사용 초는 0이다.

종료 사유: USER_HANGUP, AI_HANGUP, TIME_EXHAUSTED, NETWORK_ERROR, CANCELLED, SERVER_TIMEOUT. 시작 이벤트에는 종료 사유를 넣지 않는다. 시각은 타임존을 포함해야 하며 미래 5초 초과 또는 통화 생성 시각보다 5초 이상 이전이면 거부한다.

응답의 billingLimitSeconds와 billingDeadlineAt을 AI 서버가 이용해 미디어를 중단해야 한다. billingDeadlineAt은 API 서버의 로컬 시각이므로 AI 서버와 API 서버의 타임존을 동일하게 설정한다. 연결 대기 시간은 과금하지 않고 연결부터 종료까지의 정수 초를 원장에 기록한다. 통화 시작 시 잔액으로 최대 시간을 확정하고 그 시각을 넘긴 사용량은 차감하지 않는다. 이후 추가 구매는 다음 통화부터 적용한다.

회원 종료, AI 종료, 만료 정산이 겹쳐도 callId당 한 번 차감한다. 종료 이벤트가 유실되면 만료 스케줄러가 확정한 제한 시간으로 정산한다. 통화 중 환불돼도 실제 사용 시간은 별도 차감으로 남는다. 한 회원은 READY/IN_PROGRESS 통화를 동시에 하나만 만들 수 있다.

## 재시도, 모니터링, 백업

billing_jobs가 DB 기반 재시도 큐다. 작업별 5분 lease와 claim token으로 장애 후 중복 작업의 커밋을 막는다. 실패 간격은 지수 증가(최대 1시간), 최대 10회 후 DEAD다. Google consume의 응답을 잃었을 때는 현재 소비 상태를 먼저 조회한다.

작업 실패 로그는 토큰/서명 본문을 기록하지 않는다. billing.jobs.failed/completed 메트릭은 CloudWatch exporter 설정을 활성화하면 전송된다. 매분 원장/잔액 캐시 불일치, DEAD 작업, 5분 이상 미완료 작업을 점검해 로그를 남긴다. 불일치가 있어도 자동으로 원장을 수정하지 않는다.

infra/billing-backup-and-alerts.yaml에 Secret Manager 최소 읽기 권한, 백업용 KMS/보관 vault, RDS 연속 백업 및 35일 보존, 처리 실패/DEAD/원장 불일치/큐 지연 SNS 경보 템플릿을 추가했다. 실제 RDS ARN, 앱 IAM 역할, 로그 그룹, 키 ARN, SNS topic을 넣어 별도로 배포한다. 앱은 이 템플릿을 자동 배포하거나 Secret Manager에서 자동으로 파일을 내려받지 않는다.

DB 사용자 권한은 애플리케이션 DML, 운영 조회, 백업/복구 역할로 분리한다. 관리자 UI의 읽기/보정 권한과 별개로 운영자가 DB 원장을 직접 수정하거나 지우는 권한을 제한한다.

복구 순서:

1. 결제/시간 변경 트래픽과 큐 worker를 멈추고 장애 시각과 마지막 정상 상태를 기록한다.
2. 백업을 별도 DB로 복원하고 동일 암호화 키로 구매 참조값이 복호화되는지 확인한다.
3. 회원별 원장 합계와 잔액 캐시, 결제별 지급/회수 원장, callId별 단일 사용 기록, 큐 고유 이벤트 키를 확인한다.
4. 백업 이후 누락된 Apple 알림 이력과 Google Voided Purchases 조회 결과를 스토어에서 대조한다. 복구 DB에 이미 있는 이벤트/구매/원장 키를 유지한 상태로 재접수한다.
5. lease가 만료된 PROCESSING 작업은 자동 재처리하고, DEAD 작업은 원인을 해결한 후 관리자 API로 재시도한다.
6. 테스트 구매/환불/보정/통화 정산을 확인하고 트래픽을 재개한다. 정기 복구 훈련으로 실제 복원 시간과 데이터 손실 범위를 기록한다.

잔액 대조 SQL:

    SELECT u.id, u.remaining_talk_time, COALESCE(t.balance, 0) AS ledger_balance
    FROM users u
    LEFT JOIN (SELECT user_id, SUM(delta_seconds) balance
               FROM talk_time_transactions GROUP BY user_id) t ON t.user_id = u.id
    WHERE u.remaining_talk_time <> COALESCE(t.balance, 0);

V51은 과거 캐시 잔액과 원장 차이를 OPENING_BALANCE로 이관한다. 배포 전 백업 및 대조 후 마이그레이션을 실행한다. 실제 스토어 키, 스토어 콘솔 알림 설정, AI 미디어 종료 연동, AWS 배포와 복구 훈련은 각 환경에서 수행해야 한다.

## 검증 기준과 참고

DB 통합 테스트에서 동시 구매, 동일 거래의 중복 지급, 환불 선도착/재처리/철회/부분 회수, 원장 음수 잔액, 작업 lease, 관리자 보정과 감사, AI/회원 동시 종료, 미연결 취소와 시간 만료를 확인한다. 실제 스토어 요청은 단위 계약 테스트와 분리했으며 배포 환경에서 sandbox 구매와 RTDN/JWS를 최종 확인한다.

2026-10-11 검증: Gradle 전체 테스트 367개 중 366개 통과. 신규 billing 테스트 24개(설정 바인딩, V51 이관, 실제 DB 작업 흐름, 스토어 응답 계약)는 모두 통과했다. 유일한 실패는 기존 V9 마이그레이션의 MySQL 다중 ADD COLUMN 문법을 H2가 해석하지 못하는 contextLoads다. V9 파일은 변경하지 않았다. V51 SQL은 H2 MySQL 모드에서 별도로 실행해 잔액 이관, 고유 제약, 금융 기록 FK 보호를 확인했다. 실제 MySQL 배포와 스토어 자격증명을 이용한 통합 검증은 아직 실행하지 않았다. 백업/경보 템플릿은 YAML 구문을 검증했으며 AWS에는 배포하지 않았다.

- [Apple 공식 Java 라이브러리](https://github.com/apple/app-store-server-library-java)
- [Google ProductPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2)
- [Google 실시간 개발자 알림](https://developer.android.com/google/play/billing/rtdn-reference)
- [Pub/Sub 인증된 push](https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions)
- [AWS 연속 백업과 복구](https://docs.aws.amazon.com/aws-backup/latest/devguide/point-in-time-recovery.html)
- [AWS Backup 메트릭](https://docs.aws.amazon.com/aws-backup/latest/devguide/cloudwatch.html)
