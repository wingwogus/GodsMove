# FarmingRecordUpdateRequest

API 분류: API Request
태그: [영농일지] 기록

## Fields

- farmId: uuid, optional.
- cropId: uuid, optional.
- workTypeId: uuid, optional.
- workedAt: datetime, optional.
- memo: string, optional.
- fieldValues: RecordFieldValueRequest[], optional.
- media: RecordMediaUploadRequest[], optional.
- aiRegenerationPolicy: enum, optional. AUTO, FORCE, SKIP.

## Rule

일지 수정 시 기존 AI 분석 결과와 Coaching Feedback은 무효화하고 재생성한다. 단, 사진 추가 또는 메모 오탈자만 수정한 경우 AI 재분석을 생략할 수 있다.