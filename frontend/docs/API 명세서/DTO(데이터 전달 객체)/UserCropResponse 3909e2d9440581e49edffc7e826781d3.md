# UserCropResponse

API 분류: API Response
태그: [작물] 작물

## Fields

- id: uuid, required.
- userId: uuid, required.
- farmId: uuid, required.
- crop: CropResponse, required.
- plantingYear: number, optional.
- status: string, required.
- startedOn: date, optional.