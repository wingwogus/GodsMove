# FarmingRecordCreateRequest

API 분류: API Request
태그: [영농일지] 기록

## Fields

- farmId: uuid, required.
- cropId: uuid, required.
- workTypeId: uuid, required.
- workedAt: datetime, required.
- memo: string, optional.
- entryMode: enum, required. TEXT, VOICE.
- fieldValues: RecordFieldValueRequest[], optional.
- media: RecordMediaUploadRequest[], optional.

## Rule

필수값 worked_at, farm, crop, work_type이 없으면 저장하지 않는다. 저장 후 AI Parsing과 Coaching Feedback 생성을 비동기로 시도한다.