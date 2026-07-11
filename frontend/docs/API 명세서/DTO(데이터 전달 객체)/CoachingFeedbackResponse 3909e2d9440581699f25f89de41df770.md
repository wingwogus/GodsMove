# CoachingFeedbackResponse

API 분류: API Response
태그: [AI] 분석

## Fields

- feedbackId: uuid, required.
- recordId: uuid, required.
- status: enum, required. `PENDING`, `READY`, `FAILED`, `STALE`.
- sourceRevision: number, required.
- inputPrepared: boolean, required.
- failureCode: string, optional.
- feedback: object, optional. `READY`일 때만 존재한다.
  - goodPoint: object, required.
    - text: string, required.
  - nextActions: array, required.
    - text: string, required.
    - due: enum, required. `TODAY`, `NEXT_CHECK`, `THIS_WEEK`, `BEFORE_NEXT_WORK`.
    - category: enum, required. `IRRIGATION`, `CULTIVATION`, `PEST_CONTROL`, `HARVEST`, `RECORDING`.
- createdAt: datetime, required.
- updatedAt: datetime, required.

## Rule

`feedback`은 `PENDING`, `FAILED`, `STALE` 상태에서는 `null`이며 `READY` 상태에서만
사용자에게 보여줄 코칭 결과를 제공한다. 공개 결과는 잘한 점 텍스트와 다음 행동의
텍스트/기한/카테고리로 제한한다.

`inputSnapshot`, 내부 구조화 결과의 `basis`, `evidenceRefs`, `citations`, 모델명,
감사 상태/경고, 감사·추적용 메타데이터는 서버 내부 필드다. 클라이언트 DTO는 이
내부 필드에 의존하지 않는다.
