<!-- 자동 생성 파일 — 직접 수정하지 마세요. -->
<!-- 원본: Notion API 명세서 / 갱신: scripts/sync_api_spec.py 재실행 -->
<!-- 마지막 동기화: 2026-07-07 17:04 -->

# OnboardingResponse

> ⬆ 상위: [API 명세서](README.md)

### Body
```json
{
  "status": "REQUIRED",
  "missingFields": ["NICKNAME", "EXPERIENCE_LEVEL", "MANAGEMENT_TYPE"]
}
```
### Field
- `status`: string, required. `REQUIRED` 또는 `COMPLETE`.
- `missingFields`: string array, required. member 필수 필드 중 비어 있는 필드 목록.
### missingFields 값
- `NAME`
- `PHONE`
- `BIRTH_DATE`
- `NICKNAME`
- `EXPERIENCE_LEVEL`
- `MANAGEMENT_TYPE`
`missingFields`가 비어 있으면 `status`는 `COMPLETE`다.
### Removed
- `REGION`은 더 이상 사용하지 않는다.
- `FARM`, `CROP`은 `missingFields`로 내려주지 않고 온보딩 완료 요청 검증에서 필수값으로 처리한다.
### Parameter
None
### Header
None
### Query
None
