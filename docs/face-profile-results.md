# 얼굴 프로필 결과 처리

## 처리 흐름

1. 기존 VisualService가 S3 업로드를 검증하고 PENDING 작업을 생성한다. 커밋 후 FACE_PROFILE_BUILD 요청을 발행한다.
   요청 발행 중에도 작업 행을 잠가 빠른 결과 응답과의 상태 덮어쓰기를 방지하고, 이미 전송했거나 종료한 작업은 다시 발행하지 않는다.
2. GPU 워커가 결과 파일 업로드 후 FACE_PROFILE_BUILD_STATUS JSON을 결과 SQS에 발행한다.
3. FaceTrainingResultConsumer가 메시지를 하나씩 long polling한다. FaceTrainingResultService의 트랜잭션이 커밋된 뒤에만 메시지를 삭제한다.
4. 클론과 작업 행을 순서대로 잠그고 작업·클론·회원의 소유 관계를 검증한다. 프로필 저장, 기존 프로필 비활성화, 작업 완료, 클론 상태 계산을 하나의 트랜잭션에서 수행한다.

| 수신 상태 | DB 처리 |
| --- | --- |
| PROCESSING | 작업 PROCESSING, 최초 started_at 기록 |
| COMPLETED | 품질 통과 및 READY_FOR_RENDERING 확인 후 프로필 READY/active 저장, 작업 COMPLETED |
| FAILED + retryable=true | 오류 code/message/retryable 저장, PROCESSING 유지, finished_at 미설정 |
| FAILED + retryable=false | 오류 저장, FAILED 확정, finished_at 기록 |
| COMPLETED + qualityGatePassed=false | QUALITY_GATE_FAILED로 작업 실패, 기존 활성 프로필 유지 |

완료·최종 실패 작업에 대한 중복/지연 메시지는 무시한다. 같은 jobId의 프로필은 기존 DB 유일 제약과 행 잠금으로 중복 생성을 방지한다. 서로 다른 작업 결과가 역순으로 도착하면 더 큰 jobId의 활성 프로필을 유지하며, 오래된 작업의 결과는 비활성 프로필로 보관한다.

`retryable=true` 이벤트는 저장 후 ACK한다. 이는 GPU 요청 재발행이 아니다. 실제 작업 재시도와 최종 완료/실패 이벤트 발행은 GPU 워커의 책임이다. 워커가 후속 이벤트를 보내지 않으면 작업은 PROCESSING에 남는다.

잘못된 JSON, 알 수 없는 작업, 소유자 불일치, 결과 경로 불일치, DB 오류는 ACK하지 않는다. visibility timeout 후 재수신하고 템플릿 기준 5회 실패 시 DLQ로 이동한다. DLQ 원인을 수정한 후 운영자가 재처리해야 한다. DB 커밋 후 ACK만 실패해도 재수신은 멱등 처리된다.

## 결과 메시지 계약

```json
{
  "eventType": "FACE_PROFILE_BUILD_STATUS",
  "jobId": 1,
  "userUuid": "00000000-0000-0000-0000-000000000001",
  "cloneId": 1,
  "status": "COMPLETED",
  "result": {
    "profileStatus": "READY_FOR_RENDERING",
    "qualityGatePassed": true,
    "artifacts": {
      "bucket": "configured-AWS_S3_BUCKET",
      "profileKey": "face-results/00000000-0000-0000-0000-000000000001/job-1/face-profile.json",
      "portraitKey": "face-results/00000000-0000-0000-0000-000000000001/job-1/portrait.jpg",
      "manifestKey": "face-results/00000000-0000-0000-0000-000000000001/job-1/preprocess-manifest.json",
      "previewKey": "face-results/00000000-0000-0000-0000-000000000001/job-1/preview.mp4"
    }
  }
}
```

previewKey는 선택이다. 다른 필드는 완료 시 필수이며 bucket은 AWS_S3_BUCKET과 일치해야 한다. 키는 위 경로를 정확하게 검증한다. 알려지지 않은 추가 JSON 필드는 호환성을 위해 무시한다. 파일 내용이나 S3 존재 여부를 다시 다운로드/검사하지 않으며 업로드 후 이벤트 발행 계약을 따른다.

실패 시 result 대신 `"error": {"code":"GPU_BUSY", "message":"retry scheduled", "retryable":true}`를 보낸다. retryable은 필수이며 누락을 임의로 최종 실패로 해석하지 않는다.

## DB와 클론 READY

V33 마이그레이션은 기존 컬럼을 제거하지 않고 다음을 추가한다.

- ai_face_profiles: bucket, profile_key, portrait_key, manifest_key, quality_gate_passed
- face_training_jobs: error_code, error_retryable
- clones: status(PENDING/READY), personality_training_completed

clone_id, face_training_job_id, status, is_active는 기존 프로필 컬럼을 사용한다. portrait_key는 기존 preview_image_object_key에도 반영하고 선택 previewKey는 preview_video_object_key에 저장한다.

클론은 **음성 프로필 status=ACTIVE 및 is_active=true + 얼굴 프로필 status=READY 및 is_active=true + 성격/개인정보 학습 완료**인 경우에만 READY다. 기존 회원 UserStatus.ACTIVE와 별도다. 얼굴 완료 및 성격 완료 콜백 시 즉시 계산하고, 음성 프로필이 뒤늦게 저장되는 경우를 위해 60초 간격으로 100개씩 클론을 조회해 재계산한다. 조건을 잃으면 PENDING으로 돌아간다.

현재 코드에는 성격/개인정보 학습 완료를 확정하는 별도 기존 신호가 없어 가치관 분석 한 세트 완료를 자동으로 학습 완료로 간주하지 않는다. 학습 담당 시스템이 실제 전체 학습 완료 후 다음 콜백을 호출한다.

```http
POST /internal/clone-training/{cloneId}/personality/complete
X-Clone-Training-Callback-Secret: <CLONE_TRAINING_CALLBACK_SECRET>
```

요청 본문은 없다. 비밀값이 미설정/공백이거나 일치하지 않으면 거부한다. 콜백은 반복 호출해도 같은 상태다. 기존 음성 프로필 저장 시스템은 status=ACTIVE와 is_active=true를 함께 기록해야 한다. 이 변경은 음성 학습 결과 소비자나 성격 학습 자체를 새로 구현하지 않는다.

## 배포

1. `infra/face-result-queue.yaml`을 CloudFormation에 적용한다. 기존 GPU IAM 사용자 이름과 백엔드 EC2 IAM 역할 이름을 파라미터로 지정한다. 템플릿은 결과 큐, DLQ, GPU SendMessage 정책, 백엔드 소비 정책을 생성한다. 스택 삭제 시 큐는 보존한다.
2. 출력 ResultQueueUrl을 GPU와 백엔드의 `AWS_SQS_FACE_TRAINING_RESULT_QUEUE_URL`에 동일하게 설정한다.
3. GitHub Secrets에 `AWS_SQS_FACE_TRAINING_RESULT_QUEUE_URL`, `CLONE_TRAINING_CALLBACK_SECRET`을 추가한다. GitHub Actions Variables의 `FACE_RESULT_CONSUMER_ENABLED`를 `true`로 설정한다. deploy.yml이 EC2 .env로 전달하며 활성화 시 두 Secret 누락을 검사한다.
4. 백엔드를 배포해 Flyway V33을 적용하고 GPU 워커 환경변수도 반영/재시작한다. 백엔드 AWS 자격증명은 템플릿에서 지정한 IAM 역할을 사용해야 한다.
5. 실제 작업으로 PROCESSING→COMPLETED 및 DB 프로필 저장을 확인한다. DLQ와 오래된 PROCESSING 작업을 운영 모니터링한다.

기본값은 소비자 비활성화다. 활성화 시 결과 큐 URL은 필수다. long poll은 10초, visibility timeout은 120초, DB 결과 처리 트랜잭션 제한은 60초다. 전용 스케줄러를 사용해 기존 예약 작업과 분리한다.

이 저장소 변경만으로 AWS 스택 적용, 운영 Secret 입력, GPU 환경변수 변경이나 운영 배포가 실행되지는 않는다.

AWS 참고: [CloudFormation SQS Queue](https://docs.aws.amazon.com/ko_kr/AWSCloudFormation/latest/TemplateReference/aws-resource-sqs-queue.html).

## 검증

`./gradlew test --tests '*FaceTrainingResult*' --tests '*CloneReadinessServiceTest'`

상태 전환, 재시도 오류, 품질 실패, 소유자/경로 검증, 중복 및 역순 이벤트, 활성 프로필 교체, ACK 순서, JSON/DB 실패, READY의 8가지 조건 조합, 콜백 비밀값 검증을 포함한다. JPA 테스트에서는 독립 트랜잭션의 동시 중복 수신, 커밋/롤백, 음성 후행 완료를 확인한다. JPA 테스트는 H2 생성 스키마를 사용하므로 운영 MySQL 마이그레이션 검증을 대체하지 않는다.

기존 전체 테스트의 contextLoads는 V9 MySQL 복수 ADD COLUMN 구문이 H2에서 실패하는 문제가 있다. 기존 마이그레이션은 수정하지 않는다.

2026-09-16 검증 결과: 추가 테스트 23개 통과. 전체 106개 중 105개 통과, 위 기존 contextLoads 1개 실패. V33 단독 마이그레이션의 기존 행 보존 및 기본값도 H2 MySQL 모드에서 검증했다. 실제 AWS/GPU 연동과 운영 MySQL 적용은 별도 배포 검증이 필요하다.
