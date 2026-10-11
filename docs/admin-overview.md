# 운영 홈 집계 API

08 운영 홈 집계 요청에 맞춘 관리자 API, 목록 연결 조건, 운영 설정입니다.

## 조회와 권한

`GET /admin/overview`는 기존 관리자 JWT, 직원 활성 상태, Origin 검사와 감사 기록을 적용합니다. 카드별 권한을 매 요청 다시 확인하며 권한이 없는 카드는 `cards`에서 제외합니다. SUPER_ADMIN은 전체 카드, 기본 ADMIN_VIEWER는 서류 심사를 제외한 네 카드를 조회합니다. 직원에게 명시적으로 지정한 권한이 있으면 그 권한을 사용합니다. 관련 조회 권한이 하나도 없는 활성 직원은 빈 카드 목록을 받습니다.

| 카드 `key` | 필요한 권한 | 집계 조건 | 목록 연결 |
| --- | --- | --- | --- |
| `PENDING_DOCUMENTS` | `job_review` | 서류 요청 `PENDING` | `/admin/job-verifications` |
| `URGENT_REPORTS` | `moderation_read` | 우선순위 `URGENT`, 처리 상태 `DECIDED` 제외 | `/admin/reports?priority=URGENT&unresolvedOnly=true` |
| `UNANSWERED_SUPPORT` | `support_read` | 문의 `RECEIVED`, `IN_PROGRESS` | `/admin/support-tickets?awaitingReply=true` |
| `FAILED_TRAINING_JOBS` | `clone_training_read` | 통합 학습 상태 `FAILED`, 재시도가 생성된 원래 작업 제외 | `/admin/clone-training-jobs?status=FAILED&unresolvedOnly=true` |
| `STALLED_TRAINING_JOBS` | `clone_training_read` | `PENDING` 또는 `PROCESSING`, 마지막 활동이 정체 기준 이전 | `/admin/clone-training-jobs?stalledBefore=...` |

긴급 신고에는 심사 중·이의 제기 상태가 포함됩니다. 답변 대기 문의에는 재문의로 다시 열린 건이 포함되며 `WAITING_CUSTOMER`, `RESOLVED`, `CLOSED`는 제외합니다. 이미 등록된 답변의 메일 전송 실패는 별도 문의 전달 상태로 확인합니다.

학습은 음성·얼굴·프로필·RAG 작업을 함께 집계합니다. 마지막 활동은 원본 작업 `updatedAt`과 서버가 수락한 학습 이벤트 수신 시각 중 최신 값입니다. 중복·무시된 이벤트는 정체 시간을 연장하지 않습니다. AI의 상태·갱신 이벤트 계약은 [트윈 학습·통화 품질](twin-call-quality.md)을 그대로 사용합니다.

## 기간과 응답

선택 쿼리 `from`, `to`는 시간대가 포함된 ISO 8601 시각입니다. 기간은 **생성 시각 기준 `from <= createdAt < to`**이며 `from >= to` 또는 잘못된 날짜는 400입니다. `from` 생략은 전체 과거, `to` 생략은 요청 시작 시각 `asOf`입니다. DB의 기존 LocalDateTime 저장 방식에 맞춰 서버 시간대로 변환합니다.

집계는 기간 안에 생성된 작업의 **현재 처리 상태**를 조회합니다. 과거의 특정 시점에 어떤 상태였는지를 복원하는 통계가 아닙니다. 각 카드와 연결 목록은 같은 필터를 공유합니다. 읽는 사이 작업 상태가 바뀌면 건수가 달라질 수 있습니다.

성공 응답은 기존 `ApiResponse.result`에 아래 객체가 들어갑니다. 다음 예시는 일부 카드 조회가 실패한 경우입니다. 카드 배열은 권한에 따라 달라집니다.

```json
{
  "asOf": "2026-10-11T19:00:00+09:00",
  "generatedAt": "2026-10-11T19:00:00.030+09:00",
  "period": {"from": null, "to": "2026-10-11T19:00:00+09:00"},
  "periodBasis": "CREATED_AT",
  "source": "DIRECT",
  "consistency": "LIVE_PER_CARD",
  "dataState": "PARTIAL",
  "cards": [
    {
      "key": "UNANSWERED_SUPPORT",
      "count": null,
      "dataState": "UNAVAILABLE",
      "checkedAt": "2026-10-11T19:00:00.010+09:00",
      "durationMs": 10,
      "failureCode": "QUERY_FAILED",
      "drilldown": {
        "method": "GET",
        "path": "/admin/support-tickets",
        "query": {"awaitingReply": "true", "to": "2026-10-11T19:00:00+09:00", "page": "0", "size": "20"},
        "href": "/admin/support-tickets?awaitingReply=true&to=2026-10-11T19%3A00%3A00%2B09%3A00&page=0&size=20"
      }
    },
    {
      "key": "PENDING_DOCUMENTS",
      "count": 0,
      "dataState": "READY",
      "checkedAt": "2026-10-11T19:00:00.020+09:00",
      "durationMs": 10,
      "failureCode": null,
      "drilldown": {
        "method": "GET",
        "path": "/admin/job-verifications",
        "query": {"to": "2026-10-11T19:00:00+09:00", "page": "0", "size": "20"},
        "href": "/admin/job-verifications?to=2026-10-11T19%3A00%3A00%2B09%3A00&page=0&size=20"
      }
    }
  ]
}
```

- 카드 `READY`는 조회 성공이며 실제 빈 결과만 `count: 0`입니다. `UNAVAILABLE`은 조회 실패이며 `count: null`, `failureCode: QUERY_FAILED`입니다. 오류 원문은 응답에 넣지 않습니다.
- 전체 `COMPLETE`는 모든 허용 카드 성공, `PARTIAL`은 일부 실패, `UNAVAILABLE`은 모든 허용 카드 실패입니다. 빈 카드 배열은 `COMPLETE`입니다.
- 카드별 실패가 있어도 HTTP 200을 반환하므로 프론트는 `result.dataState`와 카드의 `dataState`를 확인해야 합니다. 조회 불가를 0건으로 표시하지 마세요.
- 권한 자체를 조회하는 DB가 실패하면 집계를 시작하지 않고 HTTP 503 / `OVERVIEW_5030`을 반환합니다. 기존 인증·접근 실패는 403입니다.
- `checkedAt`은 카드 조회 완료 시각이며 `durationMs`는 해당 조회에 걸린 시간입니다. `asOf`는 공통 기간 상한의 기본값과 정체 기준을 고정하고, `generatedAt`은 응답 생성 시각입니다.

프론트는 `drilldown.href`를 사용하거나 `path`와 `query`를 그대로 목록 요청에 전달합니다. `+09:00`의 `+`를 URL 인코딩해야 합니다. 정체 카드의 `stalledBefore`도 고정된 값을 전달합니다. 페이지 변경 시 `page`, `size`만 변경하면 됩니다. 목록 최대 크기는 기존과 같이 100입니다.

기존 목록 API에 선택 필터 `from`, `to`를 추가했습니다. 신고의 `unresolvedOnly`, 문의의 `awaitingReply`, 학습의 `unresolvedOnly`, `stalledBefore`는 기본으로 적용하지 않으므로 기존 호출은 기존 목록을 받습니다.

## DB와 운영 설정

카드마다 별도의 읽기 트랜잭션으로 DB를 직접 조회합니다. 실패한 카드의 트랜잭션은 다른 카드에 영향을 주지 않습니다. 공유 캐시나 갱신 배치는 사용하지 않으며 `source: DIRECT`, `consistency: LIVE_PER_CARD`로 이를 명시합니다. 따라서 캐시 갱신 실패·권한별 캐시 누출·오래된 집계의 위험이 없고, DB 지연·오류는 카드 상태와 모니터링으로 확인합니다.

V53 마이그레이션은 긴급 신고/문의의 기간 조회와 수락한 학습 이벤트의 최신 시각 조회를 위한 인덱스 세 개를 추가합니다. 기존 서류 상태·생성 시각 인덱스는 재사용합니다.

| 환경변수 | 기본값 | 의미 |
| --- | --- | --- |
| `ADMIN_OVERVIEW_QUERY_TIMEOUT_SECONDS` | `3` | 카드 SQL 조회 트랜잭션 타임아웃(초); 연결 확보 시간·요청 전체 시간 제한과는 별개 |
| `ADMIN_OVERVIEW_SLOW_QUERY_MS` | `1000` | 이 시간 이상 걸린 카드의 지연 경고 로그 기준 |
| `TRAINING_STALL_SECONDS` | `900` | 요청 시각에서 이 시간만큼 이전을 정체 기준으로 사용하는 기존 학습 설정 |
| `CLOUDWATCH_METRICS_ENABLED` | `false` | 기존 CloudWatch Micrometer 내보내기 사용 여부 |

Micrometer `admin.overview.card.duration`(`card`, `outcome`), `admin.overview.card.failures`(`card`), `admin.overview.request.duration`(`outcome`)을 등록하고 CloudWatch 허용 목록에 `admin.overview.`를 추가했습니다. 태그는 카드/상태 enum 및 기존 배포 구분자만 사용하며 회원·직원 ID, 원본 데이터, SQL 또는 오류 메시지를 넣지 않습니다. 실제 집계 건수는 메트릭으로 내보내지 않습니다.

고정 로그 문구:

```text
Admin overview card failed card=UNANSWERED_SUPPORT failureCode=QUERY_FAILED durationMs=...
Admin overview slow card=URGENT_REPORTS durationMs=...
Admin overview incomplete state=PARTIAL cardCount=...
```

[운영 홈 경보 템플릿](../infra/admin-overview-alerts.yaml)은 기존 애플리케이션 LogGroup과 SNS 주제 ARN을 입력받습니다. 조회 실패는 1분 동안 1회 이상, 지연 경고는 기본 5분 동안 5회 이상이면 경보를 발생시키고, 카드별 지연과 부분 실패를 대시보드에서 조회합니다. 로그 기반 지표 namespace는 `MirrorSoul/AdminOverview`입니다. Micrometer 내보내기를 켜지 않아도 애플리케이션 로그가 CloudWatch에 수집되면 동작합니다.

템플릿은 저장만 했으며 AWS 리소스에 적용하지 않았습니다. 배포 시 기존 로그 수집과 SNS 구독을 연결하고, 운영자 IAM에는 필요한 대시보드/로그 조회 권한만 부여합니다. API의 직원 권한과 AWS 콘솔 권한은 각각 관리합니다. 관련 형식은 AWS의 [MetricFilter](https://docs.aws.amazon.com/AWSCloudFormation/latest/TemplateReference/aws-resource-logs-metricfilter.html), [대시보드](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Dashboard-Body-Structure.html) 문서를 기준으로 작성했습니다.

## 검증

`AdminOverviewServiceTest`, `AdminOverviewHttpTest`, `CloudWatchMetricsConfigTest`에서 권한별 카드 제외/변경/회수, 카드 실패와 0건 구분, 실제 읽기 트랜잭션 실패 격리, 생성 시각 경계 및 시간대 인코딩, 모든 카드와 연결 목록의 건수 일치, 학습 이벤트 수락/무시와 정체 기준, 관리자 인증·Origin·감사, 메트릭 내보내기를 검증합니다.
