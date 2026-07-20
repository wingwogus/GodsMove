<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# FarmingRecordResponse

> ⬆ 상위: [API 명세서](README.md)

### Fields
- id: uuid, required.
- userId: uuid, required.
- farm: FarmResponse, required.
- crop: CropResponse, required.
- workType: WorkTypeResponse, required.
- weatherSnapshot: object | null, optional.
- workedAt: datetime, required.
- memo: string, optional.
- entryMode: enum, required. TEXT, VOICE.
- fieldValues: RecordFieldValueResponse[], required.
- media: RecordMediaResponse[], required.
- aiParseStatus: enum, optional. PENDING, SUCCEEDED, FAILED.
- coachingStatus: enum, optional. PENDING, SUCCEEDED, FAILED.
- createdAt: datetime, required.
- updatedAt: datetime, optional.
- deletedAt: datetime, optional.
### Rule
Soft Deleted 영농일지는 기본 조회에서 제외한다.
