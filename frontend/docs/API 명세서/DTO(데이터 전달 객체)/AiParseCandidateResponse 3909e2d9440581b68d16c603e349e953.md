# AiParseCandidateResponse

API 분류: API Response
태그: [AI] 분석

## Fields

- recordId: uuid, optional.
- sourceType: enum, required. TEXT, VOICE_TRANSCRIPT.
- candidates: object, required. crop, workType, fertilizerName, pesticideName, amount, memoSummary 등 후보값.
- confidence: object, optional. 필드별 신뢰도.
- unresolvedFields: string[], optional.
- status: enum, required. SUCCEEDED, FAILED.

## Rule

AI 추출 결과는 후보값이며 사용자 확인 전에는 확정 저장하지 않는다. 확실하지 않은 값은 NULL 또는 미확정 상태로 둔다.