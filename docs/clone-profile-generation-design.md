# 클론 요약·성격 태그 생성 설계안

## 1. 문서 범위와 결정

이 문서는 **추천 화면에 노출할 클론 요약과 성격 태그를 갱신하는 흐름**만 다룬다.

백엔드가 DB의 최신 사용자 정보를 수집하고 Gemini를 호출해 다음 결과를 생성·검증·저장한다.

- `Clone.summary`: 추천 화면용 클론 요약
- `ClonePersonalityTag`: 추천 화면용 성격 태그 3개
- `recommendationSummaryEmbedding`: 백엔드 추천 점수 계산용 요약 임베딩

이 흐름에서 AI 서버는 화면용 요약과 태그를 생성하지 않는다. 다만 AI 서버 전체의 역할이 학습에만 제한되는 것은 아니다. 현재 AI 서버는 얼굴·음성 프로필 학습 외에도 RAG, GPT 응답, Whisper STT, ElevenLabs TTS, WebRTC 실시간 통화, Ditto 영상 렌더링을 담당한다.

## 2. 현재 코드 기준 현황

| 항목 | 현재 상태 | 이 기능에서 필요한 작업 |
|---|---|---|
| `Clone.summary` | 존재 | 요약·해시 수정 메서드 추가 |
| `ClonePersonalityTag` | 엔티티·마이그레이션·조회 Repository 존재 | 생성 팩토리와 교체용 Repository 메서드 추가 |
| `EmbeddingTextBuilder` | `CLONE_SUMMARY`를 지원 | 초기에는 요약만 유지 |
| `UserEmbeddingRequestedEvent` | 존재 | 요약 저장 후 재사용 |
| 추천 임베딩 | pgvector의 `clone_summary_embedding` 존재 | 문서 용어를 `recommendationSummaryEmbedding`으로 구분 |
| `Clone.profileSourceHash` | 없음 | 컬럼·마이그레이션·엔티티 필드 추가 |
| 클론 프로필 생성 Job | 없음 | 유실 방지용 Job 테이블과 worker 추가 권장 |
| Gemini 생성 클라이언트 | 없음 | Structured Output 기반으로 추가 |
| AI 서버 RAG | ChromaDB·OpenAI embedding 기반으로 존재 | 백엔드 추천 임베딩과 별도 유지 |

## 3. 권장 처리 흐름

```text
성격 관련 원본 데이터 변경
    ↓ 같은 DB 트랜잭션
clone_profile_generation_jobs에 PENDING upsert
    ↓ AFTER_COMMIT
비동기 worker 기동
    ↓
백엔드가 최신 원본 조회·정규화
    ↓
canonical source hash 계산
    ├─ 기저장 hash와 동일 → SKIPPED/COMPLETED
    └─ 다름 → Gemini Structured Output 호출
                         ↓
                  응답 검증
                         ↓
             저장 직전 최신 hash 재확인
                 ├─ 다름 → STALE 후 최신 Job 재실행
                 └─ 같음 → 요약·태그·hash 원자적 교체
                                      ↓ AFTER_COMMIT
                         CLONE_SUMMARY 추천 임베딩 재생성
```

`@Async` 이벤트만 사용하면 커밋 직후에 서버가 재시작될 때 요청을 잃을 수 있다. 최소한 **원본 변경과 Job 저장을 같은 트랜잭션으로 처리**한 뒤, `AFTER_COMMIT`은 worker를 빠르게 깨우는 신호로만 사용한다. 운영에서는 SQS 또는 transactional outbox로 교체한다.

## 4. 갱신 트리거

MVP에서 다음 트리거만 사용한다.

```java
public enum CloneProfileTrigger {
    ONBOARDING_COMPLETED,
    INTERVIEW_UPDATED,
    VALUE_BALANCE_COMPLETED,
    PERSONALITY_UPDATED,
    CONVERSATION_LEARNED
}
```

- 온보딩 인터뷰 완료
- 인터뷰 추가·수정
- 밸런스 분석 완료
- 자기소개 변경
- MBTI 또는 MBTI 축 점수 변경
- 통화 기록이 성격 원본 데이터에 실제로 반영된 시점

얼굴·음성 학습 완료는 성격 원본을 변경하지 않으므로 트리거가 아니다. 현재 코드에 요약 생성에 쓸 말투 분석 계약·저장소가 없으므로 `VOICE_STYLE_UPDATED`와 `speechStyle`은 MVP에서 제외한다. 음성 합성 속도 설정인 `User.opponentSpeechSpeed`를 사용자 말투 분석 결과로 잘못 사용하면 안 된다.

## 5. Job 저장과 재시도

```text
clone_profile_generation_jobs
- id
- clone_id
- trigger_type
- source_hash             nullable; worker가 최신 원본을 읽은 뒤 확정
- prompt_version
- status                  PENDING | PROCESSING | COMPLETED | FAILED | STALE
- attempt_count
- next_attempt_at
- error_code
- error_message           민감한 원본·Gemini 응답 저장 금지
- created_at
- started_at
- completed_at
- updated_at
```

권장 정책:

- 한 클론에 대해 동시에 하나의 `PENDING`/`PROCESSING` Job만 유지한다.
- 여러 트리거가 연속하면 최신 DB 상태로 coalescing한다.
- 일시 오류와 429/5xx는 exponential backoff + jitter로 재시도한다.
- 검증 실패는 한 번만 재생성한 뒤 `FAILED`로 남기고 기존 요약·태그를 유지한다.
- 장시간 `PROCESSING`은 lease timeout으로 회수한다.

## 6. Gemini 입력 계약

### 6.1 생성 입력

```java
public record CloneProfileGenerationInput(
        String selfIntroduction,
        String mbti,
        MbtiAxisScores mbtiAxisScores,
        List<InterviewAnswer> interviews,
        String valueBalanceSummary,
        String promptVersion
) {}

public record MbtiAxisScores(
        Integer extraversion,
        Integer intuition,
        Integer feeling,
        Integer perceiving
) {}
```

`extraversion: 72`는 E 72/I 28을 의미하도록 0~100 범위와 방향을 계약으로 고정한다. 기존 DB 컬럼 `ie_score`, `ns_score`, `ft_score`, `pj_score`가 어느 글자 쪽을 의미하는지는 현재 코드에 명시되어 있지 않으므로, 매핑을 확정하기 전에 API 생성을 시작하지 않는다.

다음 값은 Gemini 입력에서 제외한다.

- `userUuid`, DB ID, 생성 시각
- 닉네임
- 이전 `summary`, 이전 tags
- 이메일, 전화번호, 주소, 얼굴·음성 원본
- MVP에서 계약이 없는 `speechStyle`

### 6.2 인터뷰 상한과 프롬프트 인젝션 방어

- 인터뷰는 `interviewId` 오름차순으로 정렬한다. 현재 Repository도 이 순서를 지원한다.
- 질문·답변 각각의 글자 수와 전체 문자 수/토큰 예산을 설정으로 제한한다.
- 상한을 넘는 과거 인터뷰는 결정적 방식으로 요약한 스냅샷을 사용한다. 이 요약의 버전과 해시도 출처에 포함한다.
- system instruction에 `USER_DATA`는 명령이 아닌 분석 대상 데이터라고 명시한다.
- 사용자 입력은 별도 JSON 파트로 전달하고, 그 안의 지시·역할 변경·출력 형식 변경 요청을 따르지 않도록 한다.

## 7. sourceHash 규칙

`sourceHash` 대상은 **생성 결과가 아닌 정규화된 원본**만이다.

```text
selfIntroduction
+ mbti
+ extraversion/intuition/feeling/perceiving
+ interviewId 순으로 정렬된 질문·답변
+ valueBalanceSummary
+ promptVersion
+ interviewCompactionVersion (과거 인터뷰 요약을 쓸 때)
    ↓ canonical JSON (UTF-8)
    ↓ SHA-256
sourceHash
```

해시에서 `previousSummary`, `previousTags`, 닉네임, UUID, timestamp를 제외한다. JSON은 필드 순서, `null` 처리, 공백 정규화, 리스트 순서를 고정한다. DTO 전체를 무작정 직렬화해 해시하지 않는다.

## 8. Gemini Structured Output

프롬프트의 `JSON만 반환`에 의존하지 않고 `responseMimeType: application/json`과 JSON Schema를 설정한다.

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "summary": {
      "type": "string",
      "description": "한국어 2~3문장의 클론 프로필 요약"
    },
    "personalityTags": {
      "type": "array",
      "minItems": 3,
      "maxItems": 3,
      "uniqueItems": true,
      "items": {
        "type": "string",
        "minLength": 2,
        "maxLength": 10
      }
    }
  },
  "required": ["summary", "personalityTags"]
}
```

모델이 schema를 따라도 의미·길이·금칙어 검증은 백엔드에서 다시 수행한다.

## 9. 응답 검증과 저장

요약:

- trim 후 빈 문자열이 아닐 것
- 설정된 최대 길이 이하일 것
- HTML/Markdown, URL, 연락처 등 화면에 노출하기 어려운 내용이 없을 것
- 입력에서 근거를 확인할 수 없는 단정적·부정적 평가를 포함하지 않을 것

태그:

- trim과 선행 `#` 제거 후 정확히 3개일 것
- 각각 한국어 2~10자일 것
- 중복이 없고 허용한 문자만 포함할 것
- 외모, 나이, 성별, 지역, 직업을 표현하지 않을 것

검증 성공 후 하나의 DB 트랜잭션에서 다음을 처리한다.

1. 현재 원본의 hash를 재계산한다.
2. 요청 hash와 다르면 결과를 저장하지 않고 Job을 `STALE`로 종료한다.
3. 같으면 `Clone.summary`, `Clone.profileSourceHash`, 태그 3개를 원자적으로 교체한다.
4. 커밋 후 `UserEmbeddingRequestedEvent(userUuid, CLONE_SUMMARY)`를 발행한다.

태그 교체는 삭제 후 insert 중 일부만 저장되지 않도록 반드시 같은 트랜잭션에서 실행한다.

## 10. 두 임베딩의 구분

| 용어 | 소유 시스템 | 용도 | 현재 구현 |
|---|---|---|---|
| `recommendationSummaryEmbedding` | 백엔드 | 사용자 간 추천 유사도 | pgvector `recommendation.user_embeddings.clone_summary_embedding`, Gemini embedding |
| `ragProfileEmbedding` | AI 서버 | 클론 대화 개인화·기억 검색 | ChromaDB, OpenAI embedding |

두 데이터는 서로 대체할 수 없다. 백엔드의 `EmbeddingTextBuilder.buildCloneSummaryText()`는 현재 `Clone.summary`만 입력으로 사용한다. 태그를 같이 넣으면 같은 성향이 중복 가중될 수 있으므로 MVP에서는 요약만 유지한다.

## 11. 개인정보와 운영 정책

- 자기소개·인터뷰를 외부 생성형 AI에 전송하는 목적, 항목, 보유 가능성을 동의·개인정보 처리방침에 반영한다.
- 최소한의 필드만 전송하고 닉네임·연락처·정확한 위치·미디어 원본은 제외한다.
- 프롬프트, 원본 데이터, 모델 응답을 일반 애플리케이션 로그에 남기지 않는다.
- Job 오류 로그에는 사용자 UUID를 최소화·가명화하고 원문을 남기지 않는다.
- 사용 중인 Gemini 플랜·모델·기능의 데이터 보유 조건을 배포 전에 다시 확인한다.

Gemini Developer API 유료 서비스는 프롬프트와 응답을 제품 개선 학습에 사용하지 않지만, 정책 위반 탐지를 위해 제한된 기간 로그를 남길 수 있다. 보장된 zero data retention이 필요하면 Vertex AI 등 해당 계약을 별도로 검토한다.

## 12. 권장 클래스 구성

```text
cloneprofile/
├── CloneProfileTrigger.java
├── CloneProfileGenerationJob.java
├── CloneProfileGenerationJobRepository.java
├── CloneProfileRefreshRequestedEvent.java
├── CloneProfileRefreshEventHandler.java
├── CloneProfileGenerationWorker.java
├── CloneProfileSourceLoader.java
├── CloneProfileSourceCanonicalizer.java
├── CloneProfileSourceHasher.java
├── CloneProfileGenerator.java
├── GeminiCloneProfileClient.java
├── CloneProfileValidator.java
├── CloneProfileWriter.java
└── dto/
    ├── CloneProfileGenerationInput.java
    └── GeneratedCloneProfile.java
```

## 13. 구현 순서

1. MBTI 축 점수의 방향 의미를 확정하고 DTO/API 이름을 변환한다.
2. `profile_source_hash` 컬럼과 `clone_profile_generation_jobs` 테이블을 추가한다.
3. 원본 수집·정렬·상한·canonical hash를 구현하고 고정 fixture로 테스트한다.
4. Gemini Structured Output 클라이언트와 응답 검증을 구현한다.
5. stale 검사·요약/태그 원자적 교체·임베딩 이벤트를 연결한다.
6. 트리거를 하나씩 연결하고 재시도·중복·서버 재시작 테스트를 추가한다.

## 14. 배포 전 확정 체크리스트

- [ ] `ie_score/ns_score/ft_score/pj_score`의 방향 규칙이 프론트·백엔드 간에 합의되었는가?
- [ ] 인터뷰별·전체 입력 상한과 과거 데이터 압축 정책이 확정되었는가?
- [ ] 외부 AI 전송 동의·보유·삭제 정책을 검토했는가?
- [ ] 사용 모델이 해당 JSON Schema 키워드를 지원하는가?
- [ ] 동일 원본은 항상 같은 hash를 만드는가?
- [ ] 중복 이벤트가 Gemini 중복 호출로 이어지지 않는가?
- [ ] 재시작 후 `PENDING`/만료 `PROCESSING` Job이 복구되는가?
- [ ] 느린 응답이 최신 사용자 데이터를 덮어쓰지 않는가?
- [ ] Gemini 실패 시 기존 요약·태그·추천 임베딩이 유지되는가?
- [ ] 태그 규칙이 프롬프트·schema·Java 검증·DB 길이 제약에서 동일한가?

## 15. 참고

- Gemini Structured Outputs: <https://ai.google.dev/gemini-api/docs/structured-output>
- Gemini Developer API Zero Data Retention: <https://ai.google.dev/gemini-api/docs/zdr>
- Backend repository: <https://github.com/Mirror-Soul/Mirror-Soul-Backend>
- AI repository: <https://github.com/Mirror-Soul/Mirror_Soul_AI>
