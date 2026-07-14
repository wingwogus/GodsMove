<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CropCategoryResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "code": "ROOT_BARK",
  "label": "뿌리/껍질"
}
```
### Field
- `code`: string, required. `CropUsePartCategory` enum code.
- `label`: string, required. 화면 표시용 카테고리명.
### Category Codes
- `WHOLE_HERB`: 전초
- `ROOT_BARK`: 뿌리/껍질
- `RHIZOME`: 뿌리줄기
- `LEAF`: 잎
- `FLOWER`: 꽃
- `FRUIT`: 열매
- `SEED`: 씨앗
- `STEM_BRANCH`: 줄기/가지
- `UNKNOWN`: 미분류
