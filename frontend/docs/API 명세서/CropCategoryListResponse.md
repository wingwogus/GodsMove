<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CropCategoryListResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "success": true,
  "data": [
    {
      "code": "ROOT_BARK",
      "label": "뿌리/껍질"
    },
    {
      "code": "LEAF",
      "label": "잎"
    }
  ],
  "error": null
}
```
### Field
- `success`: boolean, required.
- `data`: CropCategoryResponse array, required.
- `error`: object, nullable. 성공 응답에서는 `null`.
