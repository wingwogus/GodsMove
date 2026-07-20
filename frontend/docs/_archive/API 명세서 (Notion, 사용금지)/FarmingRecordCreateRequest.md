<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# FarmingRecordCreateRequest

> ⬆ 상위: [API 명세서](README.md)

### Fields
- farmId: uuid, required.
- cropId: uuid, required.
- workTypeId: uuid, required.
- workedAt: datetime, required.
- memo: string, optional.
- entryMode: enum, required. TEXT, VOICE.
- fieldValues: RecordFieldValueRequest[], optional.
- media: RecordMediaUploadRequest[], optional.
### Rule
필수값 worked_at, farm, crop, work_type이 없으면 저장하지 않는다. 저장 후 AI Parsing과 Coaching Feedback 생성을 비동기로 시도한다.
