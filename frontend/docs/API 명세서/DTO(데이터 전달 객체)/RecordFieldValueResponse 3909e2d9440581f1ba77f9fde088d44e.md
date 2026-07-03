# RecordFieldValueResponse

API 분류: API Response
태그: [영농일지] 기록

## Fields

- id: uuid, required.
- recordId: uuid, required.
- field: WorkTypeFieldResponse, required.
- valueText: string, optional.
- valueNumber: number, optional.
- valueBoolean: boolean, optional.
- valueDate: date, optional.
- valueJson: object, optional.
- createdAt: datetime, required.