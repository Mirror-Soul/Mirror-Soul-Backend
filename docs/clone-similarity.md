# 클론 완성도 점수

클론 점수는 얼굴 렌더링 품질, 음성 유사도, 성격·기억 재현도, 데이터 신뢰도를 합산한 완성도다.

## 계산 및 응답

```text
rawScore = faceScore * 0.30 + voiceScore * 0.30 + profileScore * 0.30
         + dataReliabilityScore * 0.10 - penaltyScore
syncRate = roundHalfUp(clamp(rawScore * 0.95, 0, 95), 1)
```

구성요소는 0~100이고 소수점 두 자리까지 허용한다. 감점은 0~999.99다.
점수가 없는 구성요소는 0으로 계산하며 가중치를 재분배하지 않는다.
`BigDecimal`로 계산하고 최종 값만 `HALF_UP`으로 소수점 한 자리 반올림한다.
예를 들어 얼굴 90, 음성 80, 프로필 64.25, 신뢰도 91.5, 감점 1.5이면 최종 점수는 74.0이다.

`syncRate`/`twinSyncRate` 응답은 JSON 숫자이며 소수점 한 자리를 저장·직렬화한다.
클론 상태가 READY가 아니면 내 프로필, 진화, 추천 상세, 매칭, 이력 API의 점수는 `null`이다.
프론트는 정수 전용 타입과 항상 숫자라고 가정하는 표시 로직을 변경해야 한다.
READY 조건은 기존과 동일한 활성 음성 ACTIVE + 활성 얼굴 READY + 성격 학습 완료다.
점수의 유무를 READY 조건에 추가하지 않는다.

## DB 및 기존 회원

V37은 `clones.sync_rate`를 `DECIMAL(4,1)`로 변경한다. 다음 컬럼을 추가한다.

| 컬럼 | 타입 | 용도 |
| --- | --- | --- |
| face_similarity_score | DECIMAL(5,2), nullable | 얼굴 렌더링 품질 |
| voice_similarity_score | DECIMAL(5,2), nullable | 음성 유사도 |
| profile_similarity_score | DECIMAL(5,2), nullable | 성격·기억 재현도 |
| data_reliability_score | DECIMAL(5,2), nullable | 데이터 신뢰도 |
| similarity_penalty | DECIMAL(5,2), 기본 0 | 반복·무의미 데이터 감점 |
| similarity_score_version | VARCHAR(50), nullable | 계산식 버전 |
| similarity_face_job_id | BIGINT, nullable | 마지막 반영 얼굴 작업 |
| similarity_voice_job_id | BIGINT, nullable | 마지막 반영 음성 작업 |
| similarity_profile_revision | BIGINT, nullable | 마지막 반영 RAG revision |

기존 회원의 구성요소와 버전은 NULL로 남기고 기존 종합 점수를 음성 점수로 복사하지 않는다.
네 구성요소가 모두 준비될 때까지 기존 `sync_rate`를 유지한다. 마지막 구성요소 갱신 트랜잭션에서
새 점수와 `similarity_score_version=clone-similarity-v1`을 함께 저장한다.
이후에는 누락 점수를 0으로 계산한다. 신규 가입 클론은 처음부터 v1과 0.0으로 생성한다.
기존 회원의 학습 작업을 자동 일괄 재실행하지 않는다. 필요한 얼굴·음성 재학습과 RAG 재전송은 별도 운영 절차다.

## 얼굴 결과

기존 `FACE_PROFILE_BUILD_STATUS` COMPLETED 이벤트의 `result`에 선택 필드 `faceScore`를 추가한다.

```json
{
  "profileStatus": "READY_FOR_RENDERING",
  "qualityGatePassed": true,
  "faceScore": 87.25,
  "artifacts": {
    "bucket": "configured-bucket",
    "profileKey": "face-results/{userUuid}/job-{jobId}/face-profile.json",
    "portraitKey": "face-results/{userUuid}/job-{jobId}/portrait.jpg",
    "manifestKey": "face-results/{userUuid}/job-{jobId}/preprocess-manifest.json"
  }
}
```

품질 통과 결과만 반영한다. 기존 워커가 `faceScore`를 보내지 않으면 NULL로 저장하고 점수를 추정하지 않는다.
새 활성 프로필의 점수가 없으면 이전 얼굴 구성요소를 NULL로 지워 오래된 렌더링 점수가 남지 않게 한다.
프로필 저장, 구성요소/종합 점수, 작업 완료, READY 계산은 기존 클론 행 잠금 트랜잭션에서 수행한다.
종료 작업의 중복 결과는 무시하고, 더 큰 jobId의 활성 프로필이 있으면 오래된 결과는 비활성으로 보관하며 점수를 갱신하지 않는다.

## 음성 완료

기존 음성 워커가 해당 작업의 프로필을 `status=ACTIVE`, `is_active=true`로 저장하고 커밋한 후 호출한다.
이 API가 음성 모델이나 음성 프로필을 생성하지는 않는다.

```http
POST /internal/clone-training/{cloneId}/voice/complete
Content-Type: application/json
X-Clone-Training-Callback-Secret: <공유 비밀값>
```

```json
{"jobId": 123, "voiceScore": 82.35}
```

작업과 클론의 회원 소유 관계 및 해당 작업의 활성 음성 프로필을 검증한다.
프로필 커밋보다 먼저 호출하면 400이므로 워커는 커밋 후 재시도해야 한다.
이미 반영한 jobId 이하의 요청과 더 큰 jobId의 활성 프로필이 있는 오래된 결과는 성공 응답으로 무시한다.
클론 행을 잠근 트랜잭션에서 점수를 반영하고 READY를 다시 계산한다.

## RAG 완료 및 버전 전환 호환성

백엔드의 `/api/v1/training/profiles` 요청에 `sourceRevision`을 추가했다.
AI는 전달받은 값을 변경하지 않고 완료 콜백에 돌려준다. 값은 `rag_profile_jobs.requested_revision`이다.

```http
POST /internal/clone-training/{cloneId}/personality/complete
Content-Type: application/json
X-Clone-Training-Callback-Secret: <공유 비밀값>
```

```json
{
  "calculationVersion": "clone-similarity-v1",
  "profileScore": 64.25,
  "dataReliabilityScore": 91.5,
  "penaltyScore": 1.5,
  "sourceRevision": 2
}
```

- 본문 없는 구버전 요청: 기존 성격 완료 플래그와 READY만 갱신한다. 점수는 변경하지 않는다.
- revision 없는 문서 예시 JSON: 최초 점수 저장에만 허용한다. 내부 revision 0으로 기록한다.
  이미 프로필 점수가 저장되어 있으면 성공 응답으로 무시한다.
- revision 있는 요청: 실제 해당 클론의 RAG 작업과 연결한다. 최신 요청 revision보다 작은 값은 무시하고,
  큰 값이나 작업이 없는 요청은 400으로 거부한다. 마지막 반영 revision 이하의 요청도 무시한다.

`calculationVersion`은 공식 버전이며 작업 순번을 대체하지 않는다.
revision 없는 최초 콜백끼리는 생성 순서를 알 수 없어 처음 도착한 점수만 채택한다.
반복 갱신과 정확한 역순 방어에는 AI의 `sourceRevision` 반환이 필요하다.
클론, RAG 작업 순서로 행을 잠그고 세 점수·완료 플래그·종합 점수를 한 트랜잭션에 저장한다.
지원하지 않는 계산 버전, 범위를 벗어난 점수, 필수 점수 누락은 거부한다.

## 배포 및 확인

1. 운영 MySQL에서 백업/스테이징 검증 후 Flyway V37을 적용한다. 기존 Flyway 자동 적용 경로를 따른다.
2. 프론트가 소수점 숫자와 READY 전 null을 처리하도록 반영한다.
3. AI 요청 스키마가 추가 `sourceRevision`을 수용하도록 변경하고 콜백에 반환한다.
4. 얼굴 워커의 `faceScore`와 음성 워커의 커밋 후 점수 콜백을 연결한다.
5. 신규 회원, 기존 회원의 네 구성요소 완료, 중복 및 역순 콜백을 실제 환경에서 확인한다.

자동 테스트는 계산 경계·HALF_UP·누락 점수·기존 회원 유지, 콜백 인증/검증/호환성,
동시 구성요소 갱신, 롤백 후 재시도, 최신 revision 검증, 얼굴·음성 결과 소유 관계와 중복을 확인한다.
V37 단독 테스트는 H2 MySQL 모드이며 실제 운영 MySQL/AI 연동 검증을 대체하지 않는다.

```powershell
.\gradlew.bat test --tests '*CloneSimilarity*' --tests '*CloneTrainingControllerTest' --tests '*FaceTrainingResult*' --tests '*EvolveServiceTest' --tests '*RagProfile*'
```

2026-09-28 검증 결과: 최종 관련 테스트 77개 통과, `bootJar` 생성 성공.
전체 테스트 실행은 185개 중 184개 통과이며, 실패 1개는 기존 `contextLoads()`의
V9 MySQL 복수 ADD COLUMN 구문과 H2 간 호환 오류다. 기존 V9는 변경하지 않았다.
