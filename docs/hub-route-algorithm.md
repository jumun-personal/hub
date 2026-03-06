# Hub Route 생성 정책

- 범위: 새 Hub 연결 대상. 경로 topology
- 상태 전이: [Hub Route Job 파이프라인](hub-route-job-pipeline.md) 참고

## 핵심 정책

```mermaid
flowchart TD
    subgraph Policy["경로 생성 정책"]
        P1["CENTER ↔ CENTER<br/>모든 센터 허브 간 양방향 연결"]
        P2["BRANCH ↔ CENTER<br/>지점은 소속 센터와 양방향 연결"]
        P3["BRANCH ↔ BRANCH<br/>같은 센터 소속 지점끼리 양방향 연결"]
    end
```

## CENTER Hub 생성

```mermaid
flowchart LR
    Existing["기존 COMPLETE CENTER 목록"]
    New["새 CENTER Hub"]
    Routes["기존 CENTER 각각과<br/>양방향 Route skeleton 생성"]

    Existing --> Routes
    New --> Routes
```

- 대상: 생성 시점 `COMPLETE` CENTER 전체
- 제외: 생성 중 Hub

## BRANCH Hub 생성

```mermaid
flowchart LR
    Branch["새 BRANCH Hub"]
    Center["소속 COMPLETE CENTER"]
    Siblings["같은 CENTER의<br/>기존 COMPLETE BRANCH"]
    CenterRoute["BRANCH ↔ CENTER"]
    BranchRoute["BRANCH ↔ BRANCH"]

    Branch --> CenterRoute
    Center --> CenterRoute
    Branch --> BranchRoute
    Siblings --> BranchRoute
```

- 대상: 소속 CENTER. 같은 CENTER의 기존 `COMPLETE` BRANCH
- 제약: 소속 CENTER `COMPLETE` 전 BRANCH 생성 불가

## 저장 단위

- 저장: 양방향 Route Row 2개
- 처리: Route Pair 1개 단위. 외부 API 호출·상태 관리
- 동시성: [파이프라인 문서](hub-route-job-pipeline.md#동시성-제어와-멱등성) 참고
