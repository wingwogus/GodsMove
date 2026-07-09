# CropResponse

API 분류: API Response
태그: [작물] 작물

## Fields

- id: uuid, required.
- name: string, required.
- category: string, optional.
- lifecycleType: enum, required. ANNUAL, PERENNIAL.
- defaultUnit: string, optional.

## Rule

다년생 작물은 user_crops.planting_year가 필수이다.