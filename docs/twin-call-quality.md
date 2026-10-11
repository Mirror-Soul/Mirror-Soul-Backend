# 트윈 학습·통화 품질 백엔드

07 트윈/통화 품질 구현 요청의 백엔드 API, AI 이벤트 수신 계약, DB 변경 및 운영 설정입니다. AI 모델 실행·클라이언트 계측·AWS 적용은 각 시스템에서 아래 계약에 연결해야 합니다.

## 관리자 API

모든 응답은 기존 `ApiResponse`의 `result`에 들어갑니다. 관리자 JWT와 해당 권한이 필요합니다.

| API | 권한 | 입력/결과 |
| --- | --- | --- |
| `GET /admin/clone-training-jobs` | `clone_training_read` | `kind`, `memberId`, `status`, `page=0`, `size=20`로 조회. 최대 100개 |
| `POST /admin/clone-training-jobs/{id}/retry` | `clone_training_retry` | `{ "idempotencyKey": "support-case-123" }`. 원래/새 작업 ID 및 재시도 횟수 |
| `GET /admin/calls/{id}/diagnostics` | `call_diagnostics_read` | 연결 품질, 단계 지연, 종료/오류 코드, 과금 원장 참조, 식별자 |
| `GET /admin/calls/diagnostics/summary` | `call_diagnostics_read` | 필수 `from`, `to` 및 선택 `cloneId`, `cloneVersion`, `mediaType`로 실제 p50/p95 집계 |

`ADMIN`, `ADMIN_VIEWER`의 기본 권한에 조회 권한 두 개를 추가했습니다. `clone_training_retry`는 기본 ADMIN 권한에 포함하지 않습니다. SUPER_ADMIN은 모든 권한을 갖고, ADMIN에는 기존 직원 관리 API로 별도 부여합니다. VIEWER는 재시도할 수 없습니다. 재시도 요청과 결과는 관리자 감사 이력에 남습니다.

작업 목록 필드: `trainingJobId`, `kind`, `sourceJobId`, `memberId`, `cloneId`, `cloneVersion`, `correlationId`, `stage`, `status`, `failureCode`, `retryAllowed`, `retryCount`, `retriedFromJobId`, 생성/시작/완료/갱신 시각. 회원 이름, 학습 입력, 파일 경로, 원본 오류 메시지는 포함하지 않습니다.

`kind`: `VOICE`, `FACE`, `PROFILE`(백엔드 프로필 생성), `RAG`(외부 성격 학습). `status`: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `STALE`.

## 식별자와 버전

서로 다른 테이블의 숫자 ID가 겹치므로 관리자/추적 API에서는 아래 ID를 사용합니다. 기존 SQS의 숫자 `jobId`는 유지합니다.

| 대상 | trainingJobId 예시 |
| --- | --- |
| 음성 | `VOICE-123` |
| 얼굴 | `FACE-123` |
| 프로필 생성 | `PROFILE-123` |
| 성격 학습 | `RAG-45-8` (`cloneId=45`, `sourceRevision=8`) |

`cloneVersion`은 승인된 얼굴·음성·프로필·성격 결과가 반영될 때 증가하는 클론 버전입니다. 하나의 전체 재학습 묶음 번호가 아닙니다. 기존 데이터는 버전 0입니다. 작업의 `trace.cloneVersion`은 요청 기준 버전이며, 관리자 작업 조회의 `cloneVersion`은 성공 결과 버전이 있으면 그 값을 반환합니다.

음성/얼굴 SQS 메시지와 RAG 요청에 `trace`를 추가했습니다.

```json
{
  "trace": {
    "trainingJobId": "VOICE-123",
    "cloneId": 45,
    "cloneVersion": 7,
    "correlationId": "<요청에 포함된 UUID>",
    "retryCount": 0
  }
}
```

학습 correlationId는 typed ID로부터 결정되는 UUID이므로 재전송해도 같습니다. 재시도는 새 작업 ID와 새 correlationId를 사용합니다. 통화 시작 응답과 `/internal/ai/calls/{id}/context`에 `correlationId`, 시작 시점 `cloneVersion`, 알려진 학습 결과 `trainingJobIds`를 추가했습니다. 신규 통화는 시작 시점 음성 작업의 모델을 사용합니다. 기존 통화는 알려진 모델 ID가 없으면 기존 활성 모델 조회를 사용합니다. 이 버전은 통화 시작 시점의 추적 정보이며, 사용자 인적 정보 전체를 복제해 보관하지 않습니다.

## 학습 재시도와 활성 결과 보호

`FAILED`이고 `retryAllowed=true`인 작업만 재시도합니다. 기본 최대 관리자 재시도는 3번입니다. PENDING/PROCESSING/COMPLETED/STALE 작업의 재시도는 409입니다.

동일 관리자·idempotencyKey·원래 작업의 재전송은 처음 결과를 반환합니다. 같은 키를 다른 작업에 쓰거나 이미 재시도한 부모를 다른 키로 다시 재시도하면 409입니다. 클론 행 잠금과 DB 고유 제약으로 동시 요청이 새 작업을 두 개 만들지 않도록 합니다.

음성/얼굴/프로필은 실패한 작업의 입력을 복사한 새 작업을 만듭니다. 음성/얼굴은 커밋 후 기존 SQS 발행 경로를 사용합니다. 프로필은 기존 생성 스케줄러가 처리합니다. RAG는 새 sourceRevision을 등록하고 기존 outbox가 전송합니다. 자동 RAG HTTP 재시도 횟수와 관리자 재시도 횟수를 분리했습니다.

기존 활성 음성·얼굴·성격 결과는 새 작업 생성/실패 시 유지합니다. 검증된 성공 결과를 기존 결과 처리 서비스가 승인해야 활성 결과와 버전이 바뀝니다. 이전 음성/얼굴 작업의 늦은 성공은 종료된 부모를 되살리지 않습니다. 성격 결과는 최신 sourceRevision만 반영하고, 실패 처리된 revision은 성공 콜백으로 다시 활성화하지 않습니다.

RAG 목록은 클론별 최신 revision을 보여줍니다. 지난 revision의 콜백과 재시도 기록은 이벤트/감사 테이블에 남으며, 지난 revision의 콜백은 `ignored=true`로 처리합니다. RAG 전달 성공은 학습 완료가 아닙니다. 검증된 성격 결과 콜백을 받기 전에는 `PROCESSING`으로 표시합니다.

## AI 학습 상태 콜백

`POST /internal/ai/clone-training-jobs/{trainingJobId}/events`

헤더 `X-Internal-Api-Key`, scope `training_status`가 필요합니다. 키가 없으면 401, 해당 scope가 없으면 403입니다.

```json
{
  "eventId": "voice-123-start",
  "sequence": 1,
  "state": "STARTED",
  "stage": "TRAINING",
  "occurredAt": "2026-10-11T10:00:00+09:00",
  "cloneId": 45,
  "cloneVersion": 7,
  "correlationId": "<trace.correlationId>"
}
```

`state`: `CREATED`, `STARTED`, `PROGRESS`, `COMPLETED`, `FAILED`.
`stage`: `QUEUED`, `DISPATCHING`, `PREPARING`, `TRAINING`, `VALIDATING`, `ACTIVATING`, `COMPLETED`.
CREATED는 QUEUED, COMPLETED는 COMPLETED 단계여야 합니다. STARTED/PROGRESS는 QUEUED 이후 단계입니다. FAILED에는 `failureCode`와 `retryAllowed`가 필수이고, 다른 상태에는 넣지 않습니다.

안전한 실패 코드: `DISPATCH_FAILED`, `QUALITY_GATE_FAILED`, `INVALID_INPUT`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `TRAINING_TIMEOUT`, `TRAINING_FAILED`, `AUTHORIZATION_FAILED`. 원본 스택이나 학습 입력을 보내지 않습니다.

동일 eventId·동일 본문은 `duplicate=true`로 응답합니다. 같은 eventId에 다른 본문, 같은 sequence에 다른 내용은 409입니다. 낮은 sequence, 단계 역행, terminal 이후 이벤트는 `ignored=true`로 기록합니다. 이벤트 ID와 sequence는 작업 안에서 고정·단조 증가시켜야 합니다. 콜백 cloneId/correlationId/요청 기준 cloneVersion을 검증합니다.

완료 이벤트는 상태 보고용입니다. 먼저 기존 음성/얼굴 결과 SQS 또는 성격 결과 콜백으로 실제 결과를 승인받고, 다음에 COMPLETED 이벤트를 보냅니다. 승인 전 완료 이벤트는 409입니다. 이 경우 실제 결과 처리가 커밋된 후 같은 완료 이벤트를 재전송합니다. 성격 결과 콜백에는 반드시 `sourceRevision`을 포함합니다. 기존 unversioned 콜백은 호환 용도이며 신규 작업의 버전 추적에는 사용하지 않습니다.

## 통화 품질 이벤트

`POST /internal/ai/calls/{callId}/diagnostics`

헤더 `X-Internal-Api-Key`, scope `call_diagnostics`가 필요합니다. AI/미디어 서버는 같은 응답 턴의 단계에 같은 turnId를 사용하고, 통화 시작 응답의 식별자를 그대로 전달합니다. 클라이언트 전달 완료/네트워크 상태도 AI/미디어 서버가 해당 내부 API로 전달해야 합니다.

```json
{
  "eventId": "turn-1-stt-end",
  "turnId": "turn-1",
  "step": "STT_ENDED",
  "occurredAt": "2026-10-11T10:00:01.123+09:00",
  "correlationId": "<통화 correlationId>",
  "cloneId": 45,
  "cloneVersion": 7
}
```

`step`: `STT_ENDED`, `LLM_FIRST_TOKEN`, `TTS_FIRST_AUDIO`, `VIDEO_READY`, `CLIENT_DELIVERED`, `CONNECTION`, `ERROR`. turnId는 1~100자의 영문/숫자/하이픈/밑줄입니다. 저장·조회에는 원래 turnId 대신 SHA-256 키를 사용합니다. VIDEO_READY는 영상 통화에서만 허용합니다.

CONNECTION 이벤트만 아래 `connection`을 포함합니다. 품질 판단은 보고한 서버/클라이언트의 판정입니다.

```json
{
  "quality": "POOR",
  "packetLossPercent": 2.0,
  "jitterMs": 15.0,
  "roundTripMs": 80.0,
  "bitrateKbps": 512.0
}
```

quality는 GOOD/FAIR/POOR/DISCONNECTED입니다. 개별 수치가 없으면 null을 보낼 수 있습니다. ERROR 이벤트만 `errorCode`를 포함합니다: AI_UNAVAILABLE, SIGNALING_DELIVERY_FAILED, STT_TIMEOUT, LLM_TIMEOUT, TTS_TIMEOUT, VIDEO_TIMEOUT, NETWORK_ERROR, AUTHORIZATION_FAILED, INTERNAL_ERROR.

다른 통화의 clone/version/correlation을 사용하면 409입니다. 동일 eventId 재전송은 한 번만 저장합니다. 같은 턴·단계의 시각을 바꾸면 409입니다. 이벤트 수신 순서는 자유이며, 양쪽 기준 시각이 모이면 구간을 한 번만 계산합니다. 종료보다 시작이 늦은 구간은 지연 통계에서 제외하고 `invalidStagePairs`로 표시합니다.

## 지연 집계와 진단 응답

| 측정 구간 | 기준 |
| --- | --- |
| STT_TO_LLM | STT 종료 → LLM 첫 토큰 |
| LLM_TO_TTS | LLM 첫 토큰 → TTS 첫 오디오 |
| TTS_TO_VIDEO | TTS 첫 오디오 → 영상 준비 (영상) |
| VIDEO_TO_CLIENT | 영상 준비 → 클라이언트 전달 (영상) |
| TTS_TO_CLIENT | TTS 첫 오디오 → 클라이언트 전달 (음성) |
| STT_TO_CLIENT | STT 종료 → 클라이언트 전달 (전체) |

기준 시각은 offset 포함 ISO-8601이며 내부 DB에서는 기존 서버 시간대에 맞춰 밀리초 정밀도로 저장합니다. 관련 서버 시계를 동기화해야 합니다. 미래 5초 초과, 통화/작업 생성 5초 이전, 통화 종료 5초 이후의 이벤트는 400입니다. 이벤트 도착 시각으로 지연을 추정하지 않습니다.

집계의 기간은 `[from,to)`이며 구간의 종료 시각(measuredAt) 기준입니다. 전체 통계 조회는 최대 31일입니다. 각 단계의 `sampleCount`, `p50Ms`, `p95Ms`는 저장된 실제 표본에서 계산합니다. p50/p95는 정렬된 표본의 `ceil(n*p/100)`번째 값입니다. 표본이 없으면 count=0, p50/p95=null입니다. from/to, scope(CALL/AGGREGATE), call/clone/version/media 필터를 응답에 함께 반환합니다.

개별 통화 진단은 최신 측정 구간 최대 200개와 `measurementsTruncated`, 최신 오류/안전 진단 최대 200개와 `errorsTruncated`를 반환합니다. 통계에는 잘린 목록 밖의 표본도 포함합니다. 미완성 턴 수, 잘못된 구간 수, 이벤트 수, 최신 연결 품질, 통화 종료 분류, 통화에 연결된 과금 원장 ID/사유/초 증감/잔액을 확인할 수 있습니다.

기본 관리자 진단에는 얼굴·음성 원본, 녹화 URL, 대화/인터뷰 원문, provider voiceId, 파일 objectKey, 오류 원문을 반환하지 않습니다. 새로운 품질 로그도 식별자·단계·코드·지연만 기록합니다.

## 운영 설정과 적용

V52 migration이 기존 테이블의 추적 필드와 training_lifecycle_events, training_retry_commands, call_quality_events, call_stage_measurements를 생성합니다. 이전 Flyway 파일을 수정하지 않습니다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| MAX_TRAINING_RETRIES | 3 | 관리자 재시도 최대 횟수 |
| TRAINING_DISPATCH_RECOVERY_ENABLED | false | 커밋 직후 프로세스 종료로 발행되지 않은 PENDING 음성/얼굴 작업 복구 |
| TRAINING_DISPATCH_RECOVERY_MS | 30000 | 발행 복구 주기 |
| TRAINING_STALL_SECONDS | 900 | 미종료 작업의 최근 활동 정체 기준 |
| QUALITY_MONITOR_INTERVAL_MS | 60000 | 실패/정체 관측 주기 |
| AI_INTERNAL_API_KEY | 기존 설정 | AI 내부 API 인증 |
| AI_INTERNAL_SCOPES | 기존 기본값 + call_diagnostics,training_status | 새 내부 API 접근 범위 |

운영에서 발행 복구를 사용하려면 TRAINING_DISPATCH_RECOVERY_ENABLED=true로 설정합니다. 복구는 생성 후 30초 이상 지난 PENDING 작업을 유형별 20개씩 확인합니다. 기존 발행 서비스의 행 잠금/SQS messageId 검사로 이미 발행한 작업은 건너뜁니다. FAILED 작업은 관리자 승인 재시도 경로를 사용합니다.

기존 VOICE_RESULT_ENABLED/FACE_RESULT_ENABLED, SQS URL, RAG_PROFILE_ENABLED 및 프로필 생성 설정도 실행 환경에 맞아야 합니다. 재시도 API의 성공은 작업 예약 성공이며 학습 성공을 뜻하지 않습니다. AI worker는 typed ID/기존 숫자 jobId를 기준으로 중복 실행을 방지해야 합니다. SQS 네트워크 실패 뒤에는 실제 전송 여부가 불명확할 수 있으므로 결과 콜백에도 동일한 ID를 사용합니다.

`infra/twin-call-quality-alerts.yaml`은 기존 애플리케이션 로그 그룹, 기존 4개 요청/결과 큐, 결과 DLQ 2개, 기존 SNS topic을 입력받습니다. 실패/정체, 반복 중복 콜백, 큐 메시지 체류, DLQ 경보 및 지연/재시도 대시보드를 정의합니다. 기존 큐의 redrive 정책은 큐 소유 스택에서 관리하며 이 템플릿은 새 큐를 만들지 않습니다. 재시도된 과거 실패는 미해결 실패 경보 집계에서 제외합니다. 로그 필터 지표는 관측 횟수이며 작업 개수는 관리자 API로 확인합니다.

대시보드 지연은 선택한 로그 기간의 실제 관측값을 CloudWatch `pct`로 시각화합니다. 위 API의 정확한 순위 기반 집계를 기준으로 사용합니다. 로그 그룹에 애플리케이션 로그를 보내는 기존 수집 설정이 필요합니다.

기존 로그 그룹의 보존 기간 변경은 `infra/Set-QualityLogRetention.ps1`로 설정합니다. 기본 30일, 미리보기는 `-WhatIf`입니다. 저장소에서 AWS 적용을 실행하지 않았습니다.

```powershell
.\infra\Set-QualityLogRetention.ps1 -LogGroupName '/your/backend/log-group' -RetentionDays 30 -WhatIf
```

CloudWatch 템플릿 형식은 [MetricFilter 공식 문서](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-logs-metricfilter.html)와 [Dashboard 공식 문서](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Dashboard-Body-Structure.html)를 따릅니다.

## 검증

QualityWorkflowTest: 통합 조회, 안전한 오류 분류, 권한/멱등 재시도, 동시 요청, 이전 결과 보호, 콜백 순서/중복/충돌, RAG 전달/학습 구분, 실제 p50/p95, 미완성/역행 구간, 기간·버전·통화 유형 필터.

QualityHttpTest: 실제 Spring Security 체인의 조회/재시도 권한, 커밋 후 발행, AI 인증/scope, 요청 검증, 기본 진단 정보 제한.

QualityMigrationTest: V52 적용, 과거 행 보존, 이벤트/측정 고유 제약, 음수 지연 차단. 전체 테스트의 기존 V9 MySQL DDL/H2 호환성 문제는 별도로 남아 있습니다. 실제 MySQL/AWS/AI 통합은 해당 운영 환경에서 추가 검증해야 합니다.
