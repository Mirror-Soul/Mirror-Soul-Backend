# RAG 성격 프로필 자동 학습 연동

## AI 담당자 전달 내용

백엔드에 `POST {AI_SERVER_BASE_URL}/api/v1/training/profiles` 자동 호출을 추가했습니다.
모델 파인튜닝이나 ChromaDB 직접 접근은 하지 않으며, 프로필 요약·임베딩·upsert는 AI 서버가 담당합니다.
마지막 온보딩 인터뷰 저장과 같은 트랜잭션에서 전송 작업을 DB에 등록하고, 커밋된 작업을 별도 스케줄러가 전송합니다.
얼굴/음성 학습 완료를 기다리지 않습니다. 요청 중에는 DB 잠금을 유지하지 않으므로 AI 서버의 동기 완료 콜백도 처리할 수 있습니다.

### 요청 필드

| 필드 | 백엔드 매핑 |
| --- | --- |
| `userId` | `users.uuid` 문자열. 숫자 PK가 아닙니다. |
| `cloneId` | 해당 회원의 실제 `clones.id` 숫자 |
| `aiProfileId` | `clone-{cloneId}`. 최초 전송·재시도·갱신에 동일한 값 사용 |
| `age` | 생년월일 기준 만 나이. 값이 없으면 null |
| `gender` | `male` / `female`. 값이 없으면 null |
| `mbti` | 저장된 MBTI 대문자 문자열 |
| `description` | 저장된 자기소개 |
| `interests`, `interviewTopics` | 현재 DB에 해당 필드가 없어 빈 배열 |
| `interviewSamples[].questionId` | 인터뷰 질문 ID |
| `interviewSamples[].questionCategory` | 현재 분류 필드가 없어 빈 문자열 |
| `interviewSamples[].questionText` | 저장된 질문 원문 |
| `interviewSamples[].transcript` | 저장된 `answerText`. 비어 있는 답변은 samples에서 제외 |
| `keywordLimit` | 12 |

**연동 확인 필요:** 빈 관심사·분류 및 빈 samples를 AI API가 허용하는지 확인해야 합니다.
`answerText`는 기존 인터뷰 API에서 선택 입력입니다. 백엔드가 음성 파일을 STT로 변환하지는 않으므로,
프런트 또는 STT 담당 경로가 이 값을 저장해야 인터뷰 내용이 RAG에 반영됩니다.
별도의 관심사 생성이나 질문 카테고리 추정은 하지 않습니다.

### 성공과 콜백

전송 성공은 HTTP 200, `success=true`, `status=stored`, 비어 있지 않은 `documentId`로 판정합니다.
`keywords`, `profileSummary`는 AI 서버 소유 결과로 별도 백엔드 테이블에 복제하지 않습니다.

AI 서버는 기존 계약대로 다음 콜백을 호출해야 합니다.

```http
POST /internal/clone-training/{cloneId}/personality/complete
X-Clone-Training-Callback-Secret: <공유 비밀값>
```

요청 본문은 없습니다. 기존 콜백 인증 및 처리 로직을 그대로 사용합니다.
백엔드는 AI API 성공 응답만으로 완료 플래그를 켜거나 콜백을 자체 호출하지 않습니다.
콜백으로 `personality_training_completed=true`가 되고, 활성 음성 프로필 ACTIVE 및 활성 얼굴 프로필 READY까지 충족하면 클론이 READY가 됩니다.
공유 비밀값은 AI 서버에도 동일하게 설정해야 하며, AI 서버에서 백엔드 콜백 주소로 접근할 수 있어야 합니다.
AI 서버는 저장과 콜백을 모두 완료한 후에만 성공 응답을 반환해야 합니다.

### 실패·재시도·갱신

- `rag_profile_jobs`(V35 마이그레이션)에 클론별 요청 revision, 전송 완료 revision, 재시도 시각과 임대를 저장합니다.
- 서버 재시작 후에도 미전송 작업을 다시 처리합니다. 작업자 종료 시 10분 임대 만료 후 재처리합니다.
- 연결 제한 5초, 응답 읽기 제한 120초입니다. HTTP 오류, 통신 오류, 잘못된 성공 응답을 재시도합니다.
- 재시도 간격은 30초부터 지수 증가하며 최대 1시간입니다. 자동 포기 횟수 제한은 없습니다. 4xx가 계속되면 계약/설정을 수정해야 합니다.
- 한 번에 최대 10건을 순차 처리하고, 배치 처리 후 기본 10초 대기합니다. 별도 스케줄러를 사용하여 기존 얼굴 결과 수신 등을 막지 않습니다.
- 같은 클론은 DB 잠금과 임대로 중복 작업자 처리를 억제합니다. 네트워크 타임아웃 후 원격 작업이 계속되는 경우까지 exactly-once를 보장하지 않으므로 AI의 upsert가 필요합니다.
- 전송 중 새 변경이 등록되면 이전 응답이 새 revision을 완료 처리하지 않습니다. 다음 전송 시 최신 DB 값을 다시 읽습니다.
- 초기 성격 저장(ONBOARD_C)에서는 인터뷰 완료를 기다립니다. ONBOARD_D/ACTIVE 상태의 PROFILE/INTERVIEW 이벤트는 갱신 작업을 등록합니다.
- 현재 서비스에는 가입 후 MBTI/자기소개/인터뷰를 수정하는 별도 API가 없습니다. 이후 수정 API에서 같은 트랜잭션으로 `UserEmbeddingRequestedEvent`의 PROFILE 또는 INTERVIEW를 발행하면 이 경로로 자동 전송됩니다.
- 이미 학습 완료된 클론은 갱신 중에도 기존 완료 플래그를 유지합니다. 현재 콜백은 revision이 없어 어떤 갱신의 완료인지 구분할 수 없습니다. 최신 버전 완료 여부를 READY 조건으로 사용하려면 AI와 revision 콜백 계약을 확장해야 합니다.
- 작업 확보 시 INACTIVE/DELETED 회원은 전송하지 않습니다. 이미 전송 중인 요청 취소나 AI 저장 데이터 삭제는 이 API 계약에 포함되지 않습니다.
- 프로필 원문/AI 오류 응답/비밀값을 작업 테이블이나 로그에 남기지 않습니다. 실패 유형과 cloneId만 기록합니다.

## 배포 설정

GitHub production 환경에서 설정합니다.

| 종류 | 이름 | 값 |
| --- | --- | --- |
| Variable | `RAG_PROFILE_ENABLED` | `true` (기본 false) |
| Secret | `AI_SERVER_BASE_URL` | API 서버에서 접근 가능한 AI 서버 기본 URL |
| Secret | `CLONE_TRAINING_CALLBACK_SECRET` | AI 서버와 동일한 기존 공유 비밀값 |

워크플로가 환경 파일에 위 값을 전달하며, 활성화 시 URL/콜백 비밀값 누락을 거부합니다.
수동 실행 시 동일한 환경변수를 공급합니다. 필요하면 `RAG_PROFILE_POLL_MS`를 조정할 수 있습니다(워크플로는 기본값 사용).
비활성화하면 외부 전송만 멈추고 신규 작업 등록은 계속하므로, 활성화 후 누락 없이 따라잡습니다.
V35 적용 전에는 새 애플리케이션을 실행하지 않습니다. 기존 Flyway 자동 적용 경로를 사용합니다.

## 확인 및 운영

```sql
SELECT clone_id, requested_revision, delivered_revision, attempts,
       next_attempt_at, lease_until, last_error
FROM rag_profile_jobs
WHERE requested_revision > delivered_revision
ORDER BY next_attempt_at;
```

기존 가입자의 프로필을 자동 일괄 전송하지는 않습니다. 필요한 경우 대상과 AI 부하를 확인한 후,
아래처럼 특정 완료 회원만 최초 등록/재전송할 수 있습니다. 실제 실행은 별도 운영 작업입니다.

```sql
INSERT INTO rag_profile_jobs
    (clone_id, requested_revision, delivered_revision, attempts, next_attempt_at)
SELECT c.id, 1, 0, 0, CURRENT_TIMESTAMP(6)
FROM clones c JOIN users u ON u.id = c.user_id
WHERE c.id = :clone_id AND u.status IN ('ONBOARD_D', 'ACTIVE')
ON DUPLICATE KEY UPDATE requested_revision = requested_revision + 1,
    attempts = 0, next_attempt_at = CURRENT_TIMESTAMP(6);
```

로컬 테스트는 요청 JSON, 응답 검증, 실패 전파, 저장 롤백, 동시 작업자, 임대 만료 복구,
전송 중 갱신 보존, 초기 프로필 대기 및 탈퇴 회원 제외를 확인합니다.
실제 AI 서버/ChromaDB 저장 및 원격 콜백 왕복은 배포 환경에서 확인해야 합니다.

2026-09-23 검증 결과: RAG 신규 테스트 18개 모두 통과, `bootJar` 생성 성공.
전체 테스트는 134개 중 133개 통과했습니다. 실패 1개는 기존 `contextLoads()`의
V9 `ALTER TABLE video_calls` 문법과 H2 간 호환 오류이며, 이번 V35 마이그레이션 테스트는 통과했습니다.
