# 물류 이커머스 시스템

- 범위: 물류 허브·허브 간 이동 경로·상품 재고 관리
- 담당: Hub·HubRoute 생성·갱신 파이프라인
- 구현: DB Job 기반 비동기 처리. 장애·재시도·이벤트 전달 흐름 관리
- 스택: Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Redis 7 · Kafka

## 허브 등록부터 경로 사용까지

```mermaid
flowchart LR
    Request["허브 등록 요청"] --> Save["HTTP 요청 트랜잭션<br/>Hub(PENDING) · 경로 초기 데이터 · Job 저장"]
    Save -->|커밋| DB[("PostgreSQL")]
    DB --> Response["등록 응답"]
    DB --> Worker["Worker가 Job 선점"]
    Worker --> Build["경로쌍 하나 계산"]
    Build --> Map["지도 API 호출"]
    Map --> Result["경로 결과를 DB에 저장"]
    Result --> More{"남은 경로쌍이 있나?"}
    More -->|예| Worker
    More -->|아니오| Complete["모든 경로 성공<br/>Hub COMPLETE + Outbox 저장"]
    Complete -->|트랜잭션 커밋 후 발행| Kafka["Kafka"]
    Map -->|최종 실패| Failed["Hub FAILED"]
    Failed -->|운영자 재시도| Worker
```

경로 구축이 완료된 허브만 일반 조회와 배송 경로에 포함됩니다. 상세 상태 전이와 재시도 방식은 [파이프라인 문서](docs/hub-route-job-pipeline.md#전체-상태-관계도)를 참고하세요.

## 상세 문서

- [Job·경로·Outbox 상태 전이와 재시도](docs/hub-route-job-pipeline.md)
- [센터·지점 경로 생성 정책](docs/hub-route-algorithm.md)
