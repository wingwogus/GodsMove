<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CoachingFeedbackResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
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
### Rule
MVP 피드백은 단일 영농일지 기준 1건으로 생성한다. AI 실패 시 영농일지는 정상 저장하고 피드백만 실패 처리한다.
