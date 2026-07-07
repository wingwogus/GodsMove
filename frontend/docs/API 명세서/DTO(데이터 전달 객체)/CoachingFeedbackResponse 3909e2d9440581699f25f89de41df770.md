# CoachingFeedbackResponse

API 분류: API Response
태그: [AI] 분석

## Fields

- id: uuid, required.
- userId: uuid, required.
- recordId: uuid, required for single record feedback.
- feedbackType: enum, required. RECORD, REPORT.
- summary: string, required.
- riskSignals: string[], optional.
- nextActions: string[], required.
- inputSummary: object, optional.
- sourceRefs: object, required. 근거 데이터 참조.
- modelName: string, optional.
- createdAt: datetime, required.

## Rule

MVP 피드백은 단일 영농일지 기준 1건으로 생성한다. AI 실패 시 영농일지는 정상 저장하고 피드백만 실패 처리한다.