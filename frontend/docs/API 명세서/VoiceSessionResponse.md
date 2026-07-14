<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# VoiceSessionResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- id: uuid, required.
- userId: uuid, required.
- draftRecordId: uuid, optional.
- status: enum, required. CREATED, RECORDING, PROCESSING, WAITING_CONFIRMATION, COMPLETED, CANCELLED, FAILED.
- transcript: string, optional.
- draftRecord: FarmingRecordResponse | null, optional.
- missingFields: string[], optional.
- turns: array, optional.
- createdAt: datetime, required.
- confirmedAt: datetime, optional.
