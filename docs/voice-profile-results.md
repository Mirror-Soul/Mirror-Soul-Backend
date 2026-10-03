# 음성 프로필 결과 처리

## 흐름

1. 백엔드가 기존 `VOICE_TRAINING` 요청 큐에 작업을 발행한다.
2. AI worker가 학습과 S3 업로드를 마친 뒤 음성 결과 큐에 `VOICE_TRAINING_STATUS` 메시지를 발행한다.
3. 백엔드는 결과를 받아 클론과 작업을 잠그고 `voice_training_jobs`, `ai_voice_profiles`, `clones`를 한 트랜잭션에서 갱신한다.
4. 트랜잭션이 커밋된 후 결과 메시지를 삭제한다. DB 오류나 잘못된 결과는 ACK하지 않아 큐 재시도와 DLQ로 넘긴다.

## 결과 메시지 계약

`PROCESSING`, `COMPLETED`, `FAILED`를 지원한다. 모든 메시지에 `eventType`, `jobId`, `userUuid`, `status`가 필요하다. `cloneId`는 선택이며, 보내면 백엔드가 사용자 클론과 일치하는지 검증한다. 현재 음성 학습 요청에는 `cloneId`가 없으므로 AI worker는 이 값을 DB에서 조회할 필요가 없다.

```json
{
  "eventType": "VOICE_TRAINING_STATUS",
  "jobId": 123,
  "userUuid": "00000000-0000-0000-0000-000000000001",
  "status": "COMPLETED",
  "result": {
    "elevenlabsVoiceId": "voice-model-id",
    "voiceScore": 82.35,
    "introAudio": {
      "bucket": "configured-AWS_S3_BUCKET",
      "objectKey": "voice-intros/00000000-0000-0000-0000-000000000001/job-123.mp3",
      "contentType": "audio/mpeg",
      "sizeBytes": 123456,
      "durationMs": 12000
    }
  }
}
```

`introAudio`는 선택이며, 있으면 모든 하위 필드가 필수다. `voiceScore`는 0~100, 소수점 두 자리까지다. 인트로 파일은 설정된 S3 버킷의 `voice-intros/{userUuid}/*.mp3` 경로여야 하며 최대 5 MiB, 최대 30초다. 워커는 S3 업로드를 완료한 뒤 결과를 발행한다.

실패 메시지는 `result` 대신 다음 형태의 `error`를 보낸다.

```json
{
  "eventType": "VOICE_TRAINING_STATUS",
  "jobId": 123,
  "userUuid": "00000000-0000-0000-0000-000000000001",
  "status": "FAILED",
  "error": {"code": "MODEL_ERROR", "message": "retry scheduled", "retryable": true}
}
```

`retryable=true`는 오류를 기록하고 작업을 `PROCESSING`으로 유지한다. 최종 실패(`false`)는 `FAILED`로 끝내며 기존 활성 음성 프로필을 유지한다. 같은 작업의 종료 후 중복 결과는 무시한다. 더 최신 작업이 이미 활성화된 경우 오래된 결과는 비활성 프로필로 보관하고 점수는 바꾸지 않는다.

## 배포 전 확인

V39는 작업 오류 필드를 추가하고 작업당 음성 프로필 하나를 보장하는 유일 제약을 추가한다. 기존 중복 행이 있으면 마이그레이션이 실패하므로 먼저 확인한다.

```sql
SELECT voice_training_job_id, COUNT(*)
FROM ai_voice_profiles
GROUP BY voice_training_job_id
HAVING COUNT(*) > 1;
```

1. GitHub Secret `AWS_SQS_VOICE_TRAINING_RESULT_QUEUE_URL`에 이미 만든 결과 큐 URL을 설정한다. 백엔드 IAM 역할에 해당 큐의 `ReceiveMessage`, `DeleteMessage`, `GetQueueAttributes` 권한이 있어야 한다. 큐에는 DLQ와 재전송 정책을 설정한다.
2. 백엔드를 배포해 V39가 적용되는지 확인한다. 기본값인 `VOICE_RESULT_CONSUMER_ENABLED=false`를 유지한다.
3. AI worker를 DB 직접 쓰기 대신 위 결과 메시지를 발행하도록 변경한다. 작업 성공, 실패, 재시도 이벤트를 모두 확인한다.
4. 기존 음성 완료 HTTP 콜백 호출을 중단하고 GitHub Variable `VOICE_RESULT_CONSUMER_ENABLED=true`로 소비자를 켠다. 이 설정을 켜면 기존 음성 완료 HTTP 콜백은 거부된다. 실제 결과가 DB에 반영되고 큐에서 삭제되는지 확인한다.
5. AI worker의 관련 MySQL 쓰기 권한을 회수한다. 성격 학습 완료 콜백은 계속 사용한다.

현재 저장소에는 AI worker 코드가 없어 3번과 권한 회수는 별도 작업이다. 음성 결과 큐 이름 자체는 백엔드에 고정하지 않고 URL 환경변수로 받는다.
