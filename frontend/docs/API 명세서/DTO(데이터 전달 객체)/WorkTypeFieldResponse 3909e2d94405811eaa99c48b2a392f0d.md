# WorkTypeFieldResponse

API 분류: API Response
태그: [영농일지] 기록

## Fields

- id: uuid, required.
- workTypeId: uuid, required.
- fieldKey: string, required.
- title: string, required.
- dataType: enum, required. TEXT, NUMBER, BOOLEAN, DATE, JSON.
- required: boolean, required.
- unit: string, optional.
- inputMethod: string, optional.
- description: string, optional.