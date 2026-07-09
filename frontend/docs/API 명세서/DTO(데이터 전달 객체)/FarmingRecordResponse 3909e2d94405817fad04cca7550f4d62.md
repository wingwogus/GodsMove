# FarmingRecordResponse

API 분류: API Response
태그: [영농일지] 기록

## Fields

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

## Rule

Soft Deleted 영농일지는 기본 조회에서 제외한다.