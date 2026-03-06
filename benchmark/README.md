# Benchmark reproduction

- 실행: 기능 테스트와 분리
- 보관: 실행 조건·원본 표준 출력

## HubRoute insert strategies

- 대상: PostgreSQL 16. 방향 경로 200건

비교 전략은 다음 세 가지입니다.

1. `INDIVIDUAL_TX`: 건별 INSERT + 건별 COMMIT
2. `SINGLE_TX`: 건별 INSERT + 단일 COMMIT
3. `JDBC_BULK`: JDBC `executeBatch()` + 단일 COMMIT

```bash
docker compose -f benchmark/hub-route-insert-compose.yml up -d --wait
./gradlew hubRouteInsertBenchmark
docker compose -f benchmark/hub-route-insert-compose.yml down
```

- 조건: 워밍업 10회. 측정 50회. `synchronous_commit=on`
- JDBC: `reWriteBatchedInserts=true`
- 편향 완화: 라운드별 전략 실행 순서 변경

| 구분 | 자료 |
| --- | --- |
| 원본 결과 | [`results/hub-route-insert.csv`](results/hub-route-insert.csv) |
| 측정 구현 | [`HubRouteInsertBenchmark.java`](HubRouteInsertBenchmark.java) |
| 한계 | 로컬 Docker 환경의 비교값, 운영 환경의 절대 성능 보장 아님 |

## Hub hot-key cache comparison

- 대상: 동일 Hub 단건 조회
- 비교: 캐시 미적용(`NONE`) · Redis Cache-Aside(`REDIS`)
- 환경: 독립 Spring ApplicationContext. 실제 HTTP 요청. Testcontainers PostgreSQL 16·Redis 7
- 연결: HikariCP 최대 5개

```bash
./gradlew hubCacheBenchmark --rerun-tasks
```

| 구분 | 내용 |
| --- | --- |
| 조건 | 워밍업 500회, 라운드당 3,000회, 동시성 50, 5라운드 |
| 캐시 조건 | Redis 라운드 전 warm-up, NONE도 1회 조회 후 통계 초기화 |
| 기록값 | 평균·p50·p95·p99·RPS·DB Prepared Statement 수 |
| 요약값 | 5개 라운드 지표의 중앙값 |

| 구분 | 자료 |
| --- | --- |
| 원본 결과 | [`results/hub-cache-comparison.csv`](results/hub-cache-comparison.csv) |
| 측정 구현 | [`../src/test/java/com/jumunhasyeo/hub/application/AbstractHubCacheComparisonMeasurementTest.java`](../src/test/java/com/jumunhasyeo/hub/application/AbstractHubCacheComparisonMeasurementTest.java) |

**5개 라운드 지표의 중앙값**

| 구분 | 중앙값 |
| --- | --- |
| 캐시 미적용 | 평균 `22.360ms`, p95 `49.457ms`, `2,172.21 RPS`, DB Prepared Statement `3,000회` |
| Redis Cache-Aside | 평균 `14.188ms`, p95 `26.727ms`, `3,463.37 RPS`, DB Prepared Statement `0회` |
| 변화 | 평균 `36.55%` 감소, p95 `45.96%` 감소, 처리량 `59.44%` 증가 |

한계: 로컬 단일 호스트 hot-key 비교이며, 네트워크 분리 환경·운영 트래픽 분포를 대표하지 않습니다.

## Hub create async separation

- 고정: 기존 지점 수. 지도 호출 지연
- 비교: 동기 생성 경로 · Outbox 저장까지의 비동기 요청 경로

```bash
JAVA_TOOL_OPTIONS='-DhubRoute.measurement.apiLatencyMs=2050 -DhubRoute.measurement.runs=1' \
HUB_ROUTE_MEASUREMENT_POSTGRES=true \
  ./gradlew test \
  --tests com.jumunhasyeo.hub.hub.application.HubCreateAsyncSeparationMeasurementTest \
  --rerun-tasks
```

- 범위 제외: 실제 HTTP 요청. 전체 비동기 완료 시간
- 환경: Testcontainers PostgreSQL. 고정 지연 지도 Adapter
- 수치: Hub·Outbox 저장 완료까지의 요청 구간

| 구분 | 자료 |
| --- | --- |
| 원본 결과 | [`results/hub-create-async-separation.txt`](results/hub-create-async-separation.txt) |
| 측정 구현 | [`../src/test/java/com/jumunhasyeo/hub/hub/application/HubCreateAsyncSeparationMeasurementTest.java`](../src/test/java/com/jumunhasyeo/hub/hub/application/HubCreateAsyncSeparationMeasurementTest.java) |
