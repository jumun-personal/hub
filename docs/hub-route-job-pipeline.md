# Hub Route Job 파이프라인

## 목적

- 문제: 외부 지도 API 호출. 다수 경로 저장
- 분리: HTTP 요청은 Hub·Job 저장까지만 처리
- 관리: 경로 구축 상태와 완료 이벤트 전달 상태 분리

참고: [Hub Route 생성 정책](hub-route-algorithm.md) · [README 요약](../README.md)

```mermaid
sequenceDiagram
    participant API as hub-api
    participant DB as PostgreSQL
    participant Worker as route-worker
    participant Map as Map Provider
    participant Publisher as Outbox Publisher
    participant Kafka

    API->>DB: Hub(PENDING) + Job(READY) 저장
    Worker->>DB: Planning scheduler가 Job 선점 (SKIP LOCKED)
    Worker->>DB: Route skeleton(PENDING) 저장
    Worker->>DB: Build scheduler가 경로쌍 선점 (PROCESSING)
    Worker->>Map: 거리·시간 조회
    Worker->>DB: 경로 COMPLETE + Job counter 갱신
    Worker->>DB: Hub COMPLETE + Outbox 저장
    Publisher->>Kafka: 커밋 후 HubCreatedEvent 발행
```

## 처리 단위와 책임

- Hub: 다른 서비스 사용 가능 상태 관리
- Job: 전체 경로 구축 진행도 관리
- `HubRoute`: 한 방향 경로 구축 상태 관리
- Outbox: Hub 완료 이벤트 Kafka 전달 상태 관리

## 상태 전이

저장 단위별 상태를 분리합니다. Hub·Job·Route는 경로 구축을, Outbox는 이벤트 전달을 관리합니다.

### Hub

```mermaid
stateDiagram-v2
    [*] --> PENDING: Hub 생성
    PENDING --> COMPLETE: 전체 경로 구축 성공
    PENDING --> FAILED: 전체 경로 구축 최종 실패
    FAILED --> PENDING: 운영자 수동 재시도
    COMPLETE --> [*]
```

- `PENDING`: 일반 조회 제외. 경로 구축 대기
- `COMPLETE`: 전체 경로쌍 성공. 다른 서비스 사용 가능
- 최종 실패 → Job·Hub `FAILED`. soft delete·삭제 이벤트 미발행
- 수동 재시도 → Hub `FAILED → PENDING`

### Route Build Job

```mermaid
stateDiagram-v2
    [*] --> READY: Hub 생성
    READY --> PLANNING: Planning scheduler 선점
    PLANNING --> RUNNING: skeleton 생성
    PLANNING --> PLANNING: stale claim 회수
    RUNNING --> COMPLETE: 성공 및 누락 경로 재검사 통과
    RUNNING --> FAILED: 최종 실패 경로 존재
    FAILED --> RUNNING: 내부 retry
```

- `READY`: Planning 대기
- `PLANNING`: 연결 대상 계산. Route skeleton 저장
- `RUNNING`: Route Pair 구축
- 종료: 전체 성공 → `COMPLETE` / 최종 실패 존재 → `FAILED`
- 재시도: `FAILED → RUNNING`

### 카운터와 상태 판정

- 집계 단위: Route Pair. 양방향 Route 두 행
- `total_count`: Job 전체 경로쌍 수
- `remaining_count`: 최종 결과 미확정 경로쌍 수
- `failed_count`: 최종 실패 경로쌍 수
- 일시 오류 재시도: 카운터 유지

```mermaid
flowchart LR
    More["remaining_count > 0"] --> Running["Job RUNNING<br/>Hub PENDING"]
    Done["remaining_count = 0<br/>failed_count = 0"] --> Complete["Job·Hub COMPLETE"]
    Failed["remaining_count = 0<br/>failed_count > 0"] --> FinalFailed["Job·Hub FAILED"]
```

최종 결과 확정 → `remaining_count - 1`. 최종 실패 → `failed_count + 1`. 수동 재시도 → 실패 Route 수를 `remaining_count`로 복원하고 `failed_count = 0`.

### HubRoute

- 처리 단위: 양방향 `HubRoute` 두 행. 별도 엔티티 아님
- 선점: 두 행에 같은 processing token 기록. 소유 token만 완료·실패 처리
- `PENDING`: 최초·지연 재시도 대기
- `PROCESSING`: Worker 처리 중. stale timeout 후 재선점
- `COMPLETE`: 거리·시간·공급자 반영 완료
- `FAILED`: 최대 재시도 초과·영구 오류. Job 재시도 시 `retry_count = 0` → `PENDING`

### Outbox Event

- `PENDING`: 트랜잭션 저장. Kafka 미발행
- `PROCESSING`: `SKIP LOCKED` 선점. stale timeout 후 재선점
- `COMPLETE`: Kafka 전송 성공
- `FAILED`: 최대 3회 재시도
- `DEAD`: 재시도 한도 소진. 운영자 확인

## 동시성 제어와 멱등성

### Job 선점

`READY` Job 1건 선점 → `FOR UPDATE SKIP LOCKED`. 잠긴 Job 건너뜀. 중복 Planning 방지.

### 경로쌍 선점

양방향 Route Row 선점 → `PROCESSING`·processing token 기록. 동일 token만 완료·실패 처리.

### 저장 멱등성

Route skeleton 저장 → JDBC batch. `(start_hub_id, end_hub_id, is_deleted)` UNIQUE + `ON CONFLICT DO NOTHING`. 완료 전 누락 경로 재계산.

## 지연·실패·재처리

```mermaid
sequenceDiagram
    participant Worker as route-worker
    participant Route as HubRoute pair
    participant Job as RouteBuildJob
    participant Hub as Hub
    participant Operator as 운영자

    Worker->>Route: 지도 API 호출 및 재시도
    alt 일시 오류·Rate limit
        Route-->>Worker: PENDING + next_retry_at
    else 최대 재시도 초과·영구 오류
        Worker->>Route: FAILED
        Worker->>Job: failed_count 증가
        Job->>Hub: Job FAILED와 함께 Hub FAILED
    end
    Operator->>Job: POST /internal/.../retry
    Job->>Hub: Hub PENDING
    Job->>Route: 실패 Route PENDING<br/>retry_count = 0
    Worker->>Route: 다음 구축 시도
```

- 일시 오류: Rate limit·Timeout·5xx → backoff·최대 재시도
- Provider: Kakao 실패 → 조건에 따라 Naver fallback
- Worker 중단: stale `PLANNING` Job·`PROCESSING` Route → 다음 Worker 선점

## 실행 프로필

- `hub-api`: HTTP API. Hub·Job 생성. Outbox 재발행
- `route-worker`: Planning·Build scheduler. Hub 완료 전환
- 실행 단위: Planning·Build는 같은 `route-worker` 프로세스
- 제약: 코드·scheduler 설정 수준 분리. 독립 배포·스케일링 미지원

## 전체 상태 관계도

아래 그림은 앞선 상태 설명을 한 흐름으로 묶은 참고도입니다. README에는 핵심 흐름만 표시하고, 상세 상태와 카운터는 이 문서에서 설명합니다.

```mermaid
flowchart LR
    subgraph H["Hub"]
        HP["PENDING"] --> HC["COMPLETE"]
        HP --> HF["FAILED"]
        HF -->|"수동 재시도"| HP
    end

    subgraph J["RouteBuildJob"]
        JR["READY"] --> JP["PLANNING"] --> JW["RUNNING"]
        JW --> JC["COMPLETE"]
        JW --> JF["FAILED"]
        JF -->|"retry"| JW
    end

    subgraph R["Route Pair = HubRoute 2행"]
        RP["PENDING"] --> RX["PROCESSING"] --> RC["COMPLETE"]
        RX --> RF["FAILED"]
        RF -->|"retry_count 초기화"| RP
    end

    subgraph O["Outbox"]
        OP["PENDING"] --> OX["PROCESSING"] --> OC["COMPLETE"]
        OX --> OF["FAILED"] --> OD["DEAD"]
    end
    Kafka["Kafka"]

    HP -->|"Job 생성"| JR
    JW -->|"경로쌍 실행"| RP
    RC -->|"remaining_count 감소"| JW
    RF -->|"failed_count 증가"| JF
    JF -->|"Hub 실패"| HF
    JC -->|"Hub 완료"| HC
    HC --> OP
    OC --> Kafka
```
