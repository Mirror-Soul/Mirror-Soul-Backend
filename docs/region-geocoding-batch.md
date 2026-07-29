# Region 좌표 입력 배치

이 배치는 좌표가 없는 `region` 행만 카카오 주소 검색 API로 조회한다.
응답의 법정동 코드가 `region.lawd_cd`와 일치할 때만 좌표를 저장한다.

기본 설정은 `region.geocoding.enabled=false`이므로 일반 서버 실행이나 배포만으로
외부 API 호출 및 DB 입력이 발생하지 않는다.

## 1. 배포

애플리케이션을 평소와 같이 배포한다. Flyway `V18`이 다음 컬럼을 추가한다.

- `latitude`
- `longitude`
- `coordinate_source`
- `coordinate_updated_at`

## 2. 대상 건수 확인

```sql
SELECT COUNT(*)
FROM region
WHERE latitude IS NULL
   OR longitude IS NULL;
```

## 3. 배치 한 번 실행

웹 서버를 띄우지 않는 모드로 별도 프로세스를 실행한다.

```bash
KAKAO_REST_API_KEY=발급받은키 \
java -jar build/libs/mirrorsoul-api-0.0.1-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --region.geocoding.enabled=true
```

API 호출 간격은 기본 100ms다. 필요하면 다음 환경변수로 조정한다.

```bash
REGION_GEOCODING_DELAY_MS=200
```

성공한 행에는 `coordinate_source=KAKAO_ADDRESS`가 기록된다. 검색 결과가 없거나
법정동 코드가 일치하지 않는 행은 수정하지 않고 로그에 남긴다.

## 4. 결과 확인

```sql
SELECT
    COUNT(*) AS total,
    SUM(latitude IS NOT NULL AND longitude IS NOT NULL) AS completed,
    SUM(latitude IS NULL OR longitude IS NULL) AS remaining
FROM region;
```

실패한 행:

```sql
SELECT id, lawd_cd, sido_name, sigungu_name, eupmyeondong_name
FROM region
WHERE latitude IS NULL
   OR longitude IS NULL
ORDER BY id;
```

배치를 다시 실행해도 좌표가 없는 행만 대상이 되므로 성공한 행을 중복 처리하지 않는다.
