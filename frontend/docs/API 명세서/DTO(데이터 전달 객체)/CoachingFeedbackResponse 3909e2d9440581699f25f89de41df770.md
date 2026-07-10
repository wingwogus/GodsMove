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
- createdAt: datetime, required.
- updatedAt: datetime, required.

## Rule

2단계 응답은 생성 상태만 제공한다. `inputSnapshot`, 구조화 코칭 결과, 인용,
모델 이름, 감사 결과는 노출하지 않는다. LLM이 생성할 잘한 점·다음 행동·근거
문구는 3단계에서 `READY` 결과로 추가된다.
