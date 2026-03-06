# 재고 변경 요청의 멱등성

- 대상: 재고 증감 API. Hub 경로 생성과 별개
- 위치: `StockService` 증가·감소 메서드
- 방식: `@Idempotent` AOP

## 처리 시퀀스

```mermaid
sequenceDiagram
    participant Client as Client
    participant AOP as Idempotent AOP
    participant Redis
    participant Stock as StockService
    participant DB as Database

    Client->>AOP: Idempotency-Key
    AOP->>Redis: SET NX PROCESSING
    alt 신규 키
        Redis-->>AOP: 선점 성공
        AOP->>Stock: 재고 증감 실행
        Stock->>DB: 트랜잭션 처리
        alt 커밋 성공
            AOP->>Redis: SUCCESS + TTL
        else 예외·롤백
            AOP->>Redis: 키 삭제
        end
    else 기존 PROCESSING 또는 SUCCESS
        Redis-->>AOP: 키 존재
        AOP-->>Client: 409 Conflict
    end
```

- 멱등키: 메서드 첫 번째 파라미터
- `PROCESSING` TTL: 300초
- `SUCCESS` TTL: 24시간
- 중복 요청: `409 Conflict`. 성공 응답 재생 미지원

## 범위와 한계

- 한계: DB 커밋 후 Redis 상태 기록 실패 가능
- 보완: 재고 이력 DB UNIQUE 제약 병행
