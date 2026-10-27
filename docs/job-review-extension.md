# 직업 서류 심사 확장

기존 대기 목록·상세·승인·반려·본인 결과 조회 API와 현재 직군/승인 직군 일치에 따른 공개 표시 계약을 유지한다. 서류 승인은 PASS 본인확인을 대체하지 않는다. 자기 심사와 중복 처리는 차단하며 SUPER_ADMIN도 심사할 수 있다.

## API

모든 관리자 API는 ACTIVE 관리자 + 현재 `job_review` 권한을 매 요청 검사한다.

- `GET /admin/job-verifications/history`: APPROVED/REJECTED만 조회. 선택 필터 `status`, `reviewerId`, 처리일 `from`/`to`(ISO local date-time, 양 끝 포함), `requestId`, `version`. page=0, size=20, 최대100. 처리일/id 내림차순. 응답에 requestId/version/reviewerId/status/submittedAt/reviewedAt/claimedJob/rejectionReason. 사진 URL 없음.
- 기존 상세 GET: 추가 필드 version/reviewerId/evidenceExpiresAt/evidenceState. PENDING은 job_review 관리자, 처리 후 사진은 담당 심사자 또는 SUPER_ADMIN만 조회. 만료 또는 삭제 시 photos=[]이며 상태는 EXPIRED/DELETED. 다른 심사자는 이력과 보관 상태만 확인한다.
- 기존 approve/reject POST: 선택 헤더 `X-Request-Version`(숫자). 불일치하면 거절. 응답 version 추가. 기존 헤더 없는 클라이언트도 계속 사용 가능하며 DB 행 잠금으로 중복 판정 차단.
- `GET /admin/job-verifications/{requestId}/lifecycle`: version, evidenceExpiresAt/evidenceState/evidenceDeletedAt/deletionAttempts/deletionError와 notificationState/notificationAttempts/notificationUpdatedAt/notificationError.
- `POST /admin/job-verifications/{requestId}/notification/retry`: `{ "version": 3, "possibleDuplicateAcknowledged": true }`. FAILED/UNKNOWN/DISABLED/NO_DEVICE만 재시도. FAILED/UNKNOWN은 일부 수신자에게 이미 전달되었을 수 있어 중복 가능성 확인 필수. 이전 버전과 이미 예약된 요청은 거절.

## 감사와 사진 보관

열람/판정/재시도는 기존 관리자 감사 이벤트에 actor/action/request ID/성공·실패/correlation ID를 기록한다. 판정·재시도 성공 감사는 변경과 같은 DB 트랜잭션이다. 실패·권한 거부는 독립 감사로 남는다. URL/서류/반려 자유문구/토큰은 감사 로그에 넣지 않는다.

대기 서류는 제출 후90일, 처리 후는 기본30일(`job-verification.evidence.reviewed-retention-days`, 1~365일) 보관한다. 이미 만료된 자료를 반려해도 기간을 되살리지 않는다. 만료 서류 승인은 거절한다. 서명 URL은 최대5분이며 보관 만료 시점을 넘지 않는다. 멤버 결과와 직군 표시에는 사진 삭제가 영향을 주지 않는다.

시간당 최대100건 처리하는 정리 작업은 snapshot versionId를 지정하거나 ETag If-Match 조건으로 삭제한다. 이후 같은 키의 다른 사진은 삭제하지 않는다. 미존재/ETag 불일치는 제출 snapshot이 이미 없는 것으로 간주한다. 전체 파일 삭제 성공 후 DELETED/시각 기록, 실패 시 EXPIRED 유지 + 안전한 STORAGE_DELETE_FAILED/시도 횟수 기록 후 다음 주기에 재시도한다. 부분 삭제 후 재실행 가능하다. 계정 익명화 정리 정책은 기존 별도 작업을 유지한다.

S3의 별도 lifecycle 설정은 앱 보관 시점을 앞서 삭제하지 않아야 한다. versioning, public access block, TLS, 서명 요청의 versionId/If-Match 및 signedHeaders 전달을 유지한다. 버전별 삭제 IAM은 DeleteObjectVersion, ETag 조건 삭제는 DeleteObject가 필요하다.

## 통지 outbox

판정과 PENDING 상태가 같은 트랜잭션으로 저장된다. 커밋 후 처리 및1분 주기 복구 작업이 행 잠금으로 발송권을 확보한다. 외부 Firebase 호출은 DB 트랜잭션 밖에서 한다.

NONE(과거 미추적) → PENDING → SENDING → SENT/FAILED/UNKNOWN. Firebase 비활성은 DISABLED, 수신 기기가 없으면 NO_DEVICE. 수신자 일부 실패는 FAILED, 전송 결과 불명은 UNKNOWN. SENDING 상태로10분 넘게 남으면 UNKNOWN으로 복구한다. 자동 중복 발송은 하지 않으며 명시 재시도 시 PENDING으로 돌아간다. SENT는 Firebase API 성공이며 실제 기기 열람 보장이 아니다.

V50은 기존 행 보관 기한을 제출일+90/처리일+30일로 채우고 과거 판정 통지는 NONE으로 둔다. 이전 결과를 다시 발송하지 않는다. 업그레이드 직후 오래된 서류는 정리 대상이 될 수 있다.

## 운영 적용

`infra/job-review-security-and-alerts.yaml`은 기존 S3 버킷 job-certifications/ prefix의 CloudTrail object access 로그, 전용 로그 버킷, 경보와 운영 역할용 제한 정책을 준비한다. 기존 버킷/역할/로그 그룹/SNS를 파라미터로 지정한다. 이 정책을 붙이는 것만으로 다른 기존 광역 권한이 제거되지는 않으므로 기존 역할과 버킷 정책의 광역 허용도 함께 점검한다. 템플릿과 앱 변경만 준비했으며 AWS 배포는 하지 않았다.

권한 없는 열람, 처리 후 다른 심사자 접근, 삭제 실패, 통지 실패 경보를 확인한다. CloudTrail object 데이터 이벤트는 비용이 발생하므로 해당 prefix만 기록한다. 큐는 오래된 상태부터 처리하며 FAILED/UNKNOWN은 운영 화면에서 확인하고 수동 재시도한다.

## 검증 결과

신규 테스트12개 통과: DB 권한/이력/만료/삭제/통지/동시성9개, V50 업그레이드1개, S3 snapshot 삭제1개, 실제 HTTP 권한·감사1개. 전체342개 중341개 통과. 기존 contextLoads 테스트1건은 V9 MySQL 다중 ADD COLUMN 문법과 H2 호환 오류로 실패하며, 이미 적용된 V9 파일은 변경하지 않았다.
