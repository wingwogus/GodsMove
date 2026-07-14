<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# ReissueRequest

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "refreshToken": "refresh-token"
}
```
### Field
- `refreshToken`: string, optional. Body가 없거나 값이 없으면 `refreshToken` cookie를 사용한다.
### Cookie
- `refreshToken`: string, optional. Body refreshToken보다 후순위로 사용한다.
### Header
None
### Query
None
