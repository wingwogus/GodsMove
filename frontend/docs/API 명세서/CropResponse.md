<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# CropResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "externalNo": 101,
  "name": "감초",
  "usePartCategory": "ROOT_BARK",
  "usePartCategoryLabel": "뿌리/껍질"
}
```
### Field
- `id`: uuid, required.
- `externalNo`: number, required. 산림청 약용작물 번호.
- `name`: string, required. 작물명.
- `usePartCategory`: string, required. 이용부위 카테고리 코드.
- `usePartCategoryLabel`: string, required. 화면 표시용 이용부위 카테고리명.
### Removed
- `category`, `lifecycleType`, `defaultUnit`은 사용하지 않는다.
- 재배 시작연도/상태/lifecycle 관련 규칙은 `member_crops`에서 관리하지 않는다.
