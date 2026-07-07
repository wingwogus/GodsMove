# RecordMediaResponse

API 분류: API Response
태그: [영농일지] 기록

## Fields

- id: uuid, required.
- recordId: uuid, required.
- mediaType: enum, required.
- fileUrl: string, required.
- status: string, required. UPLOADED, FAILED, DELETED 등.
- createdAt: datetime, required.