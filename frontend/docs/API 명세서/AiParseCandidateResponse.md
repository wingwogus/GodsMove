<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# AiParseCandidateResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- recordId: uuid, optional.
- sourceType: enum, required. TEXT, VOICE_TRANSCRIPT.
- candidates: object, required. crop, workType, fertilizerName, pesticideName, amount, memoSummary 등 후보값.
- confidence: object, optional. 필드별 신뢰도.
- unresolvedFields: string[], optional.
- status: enum, required. SUCCEEDED, FAILED.
### Rule
AI 추출 결과는 후보값이며 사용자 확인 전에는 확정 저장하지 않는다. 확실하지 않은 값은 NULL 또는 미확정 상태로 둔다.
