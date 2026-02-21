# Benchmark reproduction

벤치마크는 기능 테스트와 분리해 실행하며, 결과 파일에는 실행 조건과 원본 표준 출력을 함께 보관합니다.

## HubRoute insert strategies

PostgreSQL 16에서 방향 경로 200건을 다음 세 전략으로 비교합니다.

1. `INDIVIDUAL_TX`: 건별 INSERT + 건별 COMMIT
2. `SINGLE_TX`: 건별 INSERT + 단일 COMMIT
3. `JDBC_BULK`: JDBC `executeBatch()` + 단일 COMMIT

```bash
docker compose -f benchmark/hub-route-insert-compose.yml up -d --wait
./gradlew hubRouteInsertBenchmark
docker compose -f benchmark/hub-route-insert-compose.yml down
```

기본 조건은 경로 200건, 워밍업 10회, 측정 50회, `synchronous_commit=on`,
PostgreSQL JDBC `reWriteBatchedInserts=true`입니다. 측정 라운드마다 전략 실행 순서를 섞어
캐시와 체크포인트 순서 편향을 줄입니다.

- 원본 결과: [`results/hub-route-insert.csv`](results/hub-route-insert.csv)
- 측정 구현: [`HubRouteInsertBenchmark.java`](HubRouteInsertBenchmark.java)

결과는 로컬 Docker 환경의 비교값이며 운영 환경의 절대 성능을 보장하지 않습니다.
