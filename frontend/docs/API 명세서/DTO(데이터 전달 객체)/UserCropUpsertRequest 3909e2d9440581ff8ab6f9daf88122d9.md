# UserCropUpsertRequest

API 분류: API Request
태그: [작물] 작물

## Fields

- farmId: uuid, required.
- cropId: uuid, required.
- plantingYear: number, conditional. 다년생 작물은 필수.
- status: string, optional. ACTIVE, PAUSED, ENDED 등.
- startedOn: date, optional.

## Rule

주요 작물은 최소 1개 이상이어야 한다.