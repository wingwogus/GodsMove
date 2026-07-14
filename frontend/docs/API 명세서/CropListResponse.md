<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CropListResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "success": true,
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "externalNo": 101,
      "name": "감초",
      "usePartCategory": "ROOT_BARK",
      "usePartCategoryLabel": "뿌리/껍질"
    }
  ],
  "error": null
}
```
### Field
- `success`: boolean, required.
- `data`: CropResponse array, required.
- `error`: object, nullable. 성공 응답에서는 `null`.
